#!/bin/bash
# Testes do start-vortex.sh que não precisam de Docker, JVM nem rede: carrega as funções do
# launcher (source, sem rodar o main) e confere
#   - a identidade dos pid files (PID reaproveitado, outro boot ou formato antigo nunca recebem sinal);
#   - a validação do cache AOT (só uma recusa explícita da JVM apaga o cache);
#   - as chaves RSA do JWT do auth (gera quando falta, nunca sobrescreve, move do local antigo);
#   - o JWT_SECRET e as senhas do Oracle do prd, e os valores neutros dos "down";
#   - a chave pública e o emissor passados ao backend principal;
#   - o aviso do volume antigo do PostgreSQL do auth.
# Cada grupo roda numa pasta temporária, apagada no fim.
# Uso: bash scripts/test-start-vortex.sh   (sai com 1 se algum teste falhar)

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../start-vortex.sh
source "$ROOT/start-vortex.sh"
set +e

PASS=0
FAIL=0
ok() { PASS=$((PASS + 1)); printf '  ✅ %s\n' "$1"; }
nok() { FAIL=$((FAIL + 1)); printf '  ❌ %s\n' "$1"; }
check() {
    local desc=$1
    shift
    if "$@"; then ok "$desc"; else nok "$desc"; fi
}

WORK=$(mktemp -d)
SPAWNED=()
cleanup() {
    local p
    for p in "${SPAWNED[@]}"; do
        kill -KILL "$p" 2>/dev/null
    done
    rm -rf "$WORK"
}
trap cleanup EXIT

# Processo órfão (filho do init), como os que o --stop encontra de uma subida anterior: assim o
# kill -0 não enxerga um zumbi deste shell. $1 executável (padrão sleep). Imprime o PID.
spawn_orphan() {
    local bin=${1:-sleep} pid i
    pid=$( ("$bin" 300 > /dev/null 2>&1 & echo $!) )
    SPAWNED+=("$pid")
    # O $! sai antes do exec: até ele, a cmdline ainda é a deste shell e o padrão do stop_pid_file
    # não casa (no runner ubuntu-24.04 de 16/11/2026 o stop chegava antes do exec toda vez)
    for ((i = 0; i < 50; i++)); do
        [[ "$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null)" == "$bin "* ]] && break
        sleep 0.1
    done
    echo "$pid"
}

alive() { kill -0 "$1" 2>/dev/null; }
gone() { ! alive "$1"; }
wait_gone() {
    local i
    for ((i = 0; i < 40; i++)); do gone "$1" && return 0; sleep 0.1; done
    return 1
}

# ----------------------------------------------------------------------------
echo "== pid files: identidade do processo"
# shellcheck disable=SC2034  # lido pelo stop_pid_file do launcher
STOP_TIMEOUT=5

p=$(spawn_orphan)
write_pid_file "$WORK/a.pid" "$p"
check "write_pid_file grava PID, instante de início e boot_id" \
    grep -Eq "^$p [0-9]+ $(kernel_boot_id)\$" "$WORK/a.pid"
check "pid_file_live_pid devolve o PID quando é o mesmo processo" \
    test "$(pid_file_live_pid "$WORK/a.pid")" = "$p"
stop_pid_file "$WORK/a.pid" "teste" "sleep" > "$WORK/stop.out" 2>&1
if wait_gone "$p"; then
    ok "stop_pid_file encerra o processo que confere"
else
    nok "stop_pid_file encerra o processo que confere"
    # O aviso do stop_pid_file diz por que nenhum sinal saiu; sem ele a falha no CI não tem pista
    sed 's/^/      /' "$WORK/stop.out"
    printf '      stat: %s\n      cmdline: %s\n      sleep: %s\n' \
        "$(cat "/proc/$p/stat" 2>/dev/null)" \
        "$(tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null)" \
        "$(readlink -f "$(command -v sleep)")"
fi
check "stop_pid_file apaga o pid file" test ! -e "$WORK/a.pid"

p=$(spawn_orphan)
start=$(proc_start_ticks "$p")
printf '%s %s %s\n' "$p" "$((start - 1))" "$(kernel_boot_id)" > "$WORK/b.pid"
out=$(stop_pid_file "$WORK/b.pid" "teste" "sleep")
check "PID reaproveitado (outro instante de início) não recebe sinal" alive "$p"
check "...e o pid file velho é descartado com aviso" \
    bash -c '[[ ! -e "$1" && "$2" == *"agora é outro processo"* ]]' _ "$WORK/b.pid" "$out"
printf '%s %s %s\n' "$p" "$((start - 1))" "$(kernel_boot_id)" > "$WORK/b2.pid"
check "pid_file_live_pid não devolve PID reaproveitado" test -z "$(pid_file_live_pid "$WORK/b2.pid")"

printf '%s %s %s\n' "$p" "$start" "00000000-0000-0000-0000-000000000000" > "$WORK/c.pid"
stop_pid_file "$WORK/c.pid" "teste" "sleep" > /dev/null
check "pid file de outro boot do kernel não recebe sinal" alive "$p"

echo "$p" > "$WORK/d.pid"
stop_pid_file "$WORK/d.pid" "teste" "sleep" > /dev/null
check "pid file no formato antigo (só o PID) não recebe sinal" alive "$p"

write_pid_file "$WORK/e.pid" "$p"
stop_pid_file "$WORK/e.pid" "teste" "java" > /dev/null
check "mesmo processo, mas linha de comando de outro programa: não recebe sinal" alive "$p"

kill -KILL "$p"
wait_gone "$p"
printf '%s %s %s\n' "$p" "$start" "$(kernel_boot_id)" > "$WORK/f.pid"
stop_pid_file "$WORK/f.pid" "teste" "sleep" > /dev/null
check "processo que já saiu: pid file apagado sem erro" test ! -e "$WORK/f.pid"

# comm com espaço e parênteses: o campo 2 do /proc/PID/stat não pode deslocar os outros
cp "$(command -v sleep)" "$WORK/a) b (c"
p=$(spawn_orphan "$WORK/a) b (c")
ticks=$(proc_start_ticks "$p")
btime=$(awk '/^btime/ { print $2 }' /proc/stat)
hz=$(getconf CLK_TCK)
calc=$((btime + ticks / hz))
real=$(date -d "$(ps -o lstart= -p "$p")" +%s)
check "proc_start_ticks lê o campo certo com comm 'a) b (c' (calculado $calc, ps $real)" \
    test $((calc > real ? calc - real : real - calc)) -le 2
kill -KILL "$p"

# ----------------------------------------------------------------------------
echo "== cache AOT: só a recusa explícita da JVM apaga o cache"
mkdir -p "$WORK/bin" "$WORK/aot/logs" "$WORK/aot/svc/target"
cat > "$WORK/bin/java" << 'EOF'
#!/bin/bash
# java falso: com -XX:AOTMode=on e FAKE_JAVA=recusa, imita a recusa do HotSpot; senão, morre por outro motivo
for a in "$@"; do
    if [[ "$a" == "-XX:AOTMode=on" && "$FAKE_JAVA" == "recusa" ]]; then
        echo "[0.002s][error  ][aot] Unable to map shared spaces"
        echo "Error occurred during initialization of VM"
        echo "Unable to use AOT cache."
        exit 1
    fi
done
echo "APPLICATION FAILED TO START (args: $*)"
exit 1
EOF
chmod +x "$WORK/bin/java"

run_fake_jvm() {
    (
        cd "$WORK/aot" || exit 99
        export PATH="$WORK/bin:$PATH"
        # shellcheck disable=SC2034  # lidos pelo launch_leve_jvm do launcher
        LAUNCH_ENV=("FAKE_JAVA=$1")
        # shellcheck disable=SC2034
        LAUNCH_ARGS=(-jar app.jar)
        launch_leve_jvm "svc" "JVM de teste" "svc" "target/app.aot" "http://127.0.0.1:9/health" "-Xmx16m"
    ) > "$WORK/aot/out.txt" 2>&1
}

touch "$WORK/aot/svc/target/app.aot"
echo "carimbo" > "$WORK/aot/svc/target/app.aot.stamp"
run_fake_jvm recusa
rc=$?
check "recusa do cache: launch_leve_jvm falha (a subida sem cache também morreu)" test "$rc" -ne 0
check "recusa do cache: app.aot e o carimbo são apagados" \
    test ! -e "$WORK/aot/svc/target/app.aot" -a ! -e "$WORK/aot/svc/target/app.aot.stamp"
check "recusa do cache: o log da recusa fica em svc-aot-falhou.log" \
    grep -q "Unable to use AOT cache" "$WORK/aot/logs/svc-aot-falhou.log"
check "recusa do cache: sobe de novo sem -XX:AOTCache" \
    bash -c 'grep -q "APPLICATION FAILED TO START" "$1" && ! grep -q "AOTCache" "$1"' _ "$WORK/aot/logs/svc.log"

rm -f "$WORK/aot/logs/"*
touch "$WORK/aot/svc/target/app.aot"
echo "carimbo" > "$WORK/aot/svc/target/app.aot.stamp"
run_fake_jvm outro
rc=$?
check "outra morte na validação: launch_leve_jvm falha" test "$rc" -ne 0
check "outra morte na validação: app.aot e o carimbo ficam" \
    test -e "$WORK/aot/svc/target/app.aot" -a -e "$WORK/aot/svc/target/app.aot.stamp"
check "outra morte na validação: nenhum svc-aot-falhou.log" test ! -e "$WORK/aot/logs/svc-aot-falhou.log"
check "outra morte na validação: o erro real aparece na saída" \
    grep -q "APPLICATION FAILED TO START" "$WORK/aot/out.txt"

# ----------------------------------------------------------------------------
echo "== chaves JWT do auth"
sha() { sha256sum "$1" | cut -d' ' -f1; }
pair_ok() { [[ "$(private_key_public_der_sha "$1/privateKey.pem")" == "$(public_key_der_sha "$1/publicKey.pem")" ]]; }

k="$WORK/k1"
ensure_auth_jwt_keys "$k/jwt" "$k/legado" > /dev/null
check "sem chaves: gera o par" test -s "$k/jwt/privateKey.pem" -a -s "$k/jwt/publicKey.pem"
check "privada em PKCS#8 (BEGIN PRIVATE KEY)" grep -qx -- "-----BEGIN PRIVATE KEY-----" "$k/jwt/privateKey.pem"
check "pública em X.509 (BEGIN PUBLIC KEY), o formato do AuthTokenVerifier" \
    grep -qx -- "-----BEGIN PUBLIC KEY-----" "$k/jwt/publicKey.pem"
check "RSA 2048" bash -c 'openssl pkey -in "$1" -text -noout 2>/dev/null | grep -q "2048 bit"' _ "$k/jwt/privateKey.pem"
check "as duas são do mesmo par" pair_ok "$k/jwt"
check "privada só para o dono (600)" test "$(stat -c %a "$k/jwt/privateKey.pem")" = "600"
s1=$(sha "$k/jwt/privateKey.pem")
s2=$(sha "$k/jwt/publicKey.pem")
ensure_auth_jwt_keys "$k/jwt" "$k/legado" > /dev/null
check "par existente: nada é sobrescrito" \
    test "$(sha "$k/jwt/privateKey.pem")$(sha "$k/jwt/publicKey.pem")" = "$s1$s2"

k2="$WORK/k2"
mkdir -p "$k2/jwt"
cp -p "$k/jwt/privateKey.pem" "$k2/jwt/"
ensure_auth_jwt_keys "$k2/jwt" "$k2/legado" > /dev/null
check "só a privada: deriva a pública dela, sem tocar na privada" \
    bash -c '[[ "$(sha256sum < "$1/privateKey.pem")" == "$(sha256sum < "$2/privateKey.pem")" ]]' _ "$k2/jwt" "$k/jwt"
check "só a privada: a pública derivada é a do par" pair_ok "$k2/jwt"

k3="$WORK/k3"
mkdir -p "$k3/jwt"
cp -p "$k/jwt/publicKey.pem" "$k3/jwt/"
ensure_auth_jwt_keys "$k3/jwt" "$k3/legado" > /dev/null
rc=$?
check "só a pública: falha" test "$rc" -ne 0
check "só a pública: não cria privada nem troca a pública" \
    test ! -e "$k3/jwt/privateKey.pem" -a "$(sha "$k3/jwt/publicKey.pem")" = "$s2"

k4="$WORK/k4"
mkdir -p "$k4/legado"
cp -p "$k/jwt/privateKey.pem" "$k/jwt/publicKey.pem" "$k4/legado/"
ensure_auth_jwt_keys "$k4/jwt" "$k4/legado" > /dev/null
check "par só em META-INF/resources: movido para jwt/ sem mudar as chaves" \
    test "$(sha "$k4/jwt/privateKey.pem")$(sha "$k4/jwt/publicKey.pem")" = "$s1$s2"
check "par só em META-INF/resources: some do local servido como estático" \
    test ! -e "$k4/legado/privateKey.pem" -a ! -e "$k4/legado/publicKey.pem"

k5="$WORK/k5"
ensure_auth_jwt_keys "$k5/outro" "$k5/legado" > /dev/null
mkdir -p "$k5/jwt"
cp -p "$k/jwt/privateKey.pem" "$k5/jwt/"
cp -p "$k5/outro/publicKey.pem" "$k5/jwt/"
s3=$(sha "$k5/jwt/publicKey.pem")
ensure_auth_jwt_keys "$k5/jwt" "$k5/legado" > /dev/null
rc=$?
check "privada e pública de pares diferentes: falha" test "$rc" -ne 0
check "pares diferentes: nada é sobrescrito" \
    test "$(sha "$k5/jwt/privateKey.pem")$(sha "$k5/jwt/publicKey.pem")" = "$s1$s3"

k6="$WORK/k6"
mkdir -p "$k6/jwt"
: > "$k6/jwt/privateKey.pem"
: > "$k6/jwt/publicKey.pem"
ensure_auth_jwt_keys "$k6/jwt" "$k6/legado" > /dev/null
rc=$?
check "arquivos vazios: falha sem sobrescrever" test "$rc" -ne 0 -a ! -s "$k6/jwt/privateKey.pem"

# ----------------------------------------------------------------------------
echo "== JWT_SECRET do prd"
secret_rejected() { ! JWT_SECRET="$1" check_prd_jwt_secret > /dev/null; }
secret_accepted() { JWT_SECRET="$1" check_prd_jwt_secret > /dev/null; }
check "vazio é recusado" secret_rejected ""
check "o padrão do application.properties é recusado" secret_rejected "CHANGE_THIS_SECURE_SECRET_AT_LEAST_32_CHARS"
check "o padrão antigo do docker-compose.auth.yml é recusado" secret_rejected "vortex-super-secret-jwt-key-change-in-production"
check "menos de 32 caracteres é recusado" secret_rejected "Xk9fQ2mZ7vR4tB8n"
check "só minúsculas e números, com menos de 50, é recusado" secret_rejected "abcdefghijklmnopqrstuvwxyz0123456789"
check "aleatório com maiúsculas, minúsculas e números é aceito" secret_accepted "Xk9fQ2mZ7vR4tB8nL1pW6yH3cJ5dG0sA-uE"
check "saída do openssl rand -base64 48 é aceita" secret_accepted "$(openssl rand -base64 48)"

# ----------------------------------------------------------------------------
echo "== senhas do Oracle do prd"
forte=$(openssl rand -hex 24)
oracle_rejected() { ! ORACLE_PASSWORD="$1" ORACLE_APP_USER_PASSWORD="$2" check_oracle_passwords > /dev/null; }
oracle_accepted() { ORACLE_PASSWORD="$1" ORACLE_APP_USER_PASSWORD="$2" check_oracle_passwords > /dev/null; }
check "as duas ausentes são recusadas" oracle_rejected "" ""
check "só a do vortex_user ausente é recusada" oracle_rejected "$forte" ""
check "o antigo padrão Oracle_1234 de SYS e SYSTEM é recusado" oracle_rejected "Oracle_1234" "$forte"
check "o antigo padrão vortex_password do vortex_user é recusado" oracle_rejected "$forte" "vortex_password"
check "admin em qualquer caixa é recusado" oracle_rejected "Meu-ADMIN-2026-xyz" "$forte"
check "só letras é recusado" oracle_rejected "$forte" "abcdefghijklmnopQ"
check "só números é recusado" oracle_rejected "$forte" "123456789012345"
check "saída do openssl rand -hex 24 nas duas é aceita" oracle_accepted "$forte" "$(openssl rand -hex 24)"
check "12 caracteres misturados é o mínimo aceito" oracle_accepted "a1b2c3d4e5f6" "$forte"
check "11 caracteres misturados é recusado" oracle_rejected "a1b2c3d4e5f" "$forte"
# O locale de quem roda não muda a regra: em C o bash conta bytes, e 8 caracteres com acento dão 14
oracle_rejected_in_locale() { local LC_ALL=$1; shift; oracle_rejected "$@"; }
oracle_accepted_in_locale() { local LC_ALL=$1; shift; oracle_accepted "$@"; }
check "8 caracteres com acento são recusados com LC_ALL=C" oracle_rejected_in_locale C "ééééééa1" "$forte"
check "acento é recusado também com 12 caracteres ou mais em C.UTF-8" oracle_rejected_in_locale C.UTF-8 "ééééééa1bcdef" "$forte"
check "12 caracteres ASCII misturados continuam aceitos com LC_ALL=C" oracle_accepted_in_locale C "a1b2c3d4e5f6" "$forte"
# A dica regera só o que falhou: regerar uma senha boa de um volume já criado daria ORA-01017
oracle_hint() { ORACLE_PASSWORD="$1" ORACLE_APP_USER_PASSWORD="$2" check_oracle_passwords 2>&1 | grep -F 'export '; }
hint_only_sys() {
    local hint
    hint=$(oracle_hint "Oracle_1234" "$forte")
    [[ "$hint" == *'ORACLE_PASSWORD="$(openssl rand -hex 24)"'* && "$hint" != *ORACLE_APP_USER_PASSWORD* ]]
}
hint_both() {
    local hint
    hint=$(oracle_hint "" "")
    [[ "$hint" == *'ORACLE_PASSWORD="$('* && "$hint" == *'ORACLE_APP_USER_PASSWORD="$('* ]]
}
check "com só ORACLE_PASSWORD fraca, a dica não regera a do vortex_user" hint_only_sys
check "com as duas faltando, a dica gera as duas" hint_both

# docker falso: imprime o JWT_SECRET e as senhas do Oracle que o comando recebeu
docker() { echo "JWT_SECRET=${JWT_SECRET:-} ORACLE_PASSWORD=${ORACLE_PASSWORD:-} ORACLE_APP_USER_PASSWORD=${ORACLE_APP_USER_PASSWORD:-}"; }
out=$(unset JWT_SECRET ORACLE_PASSWORD ORACLE_APP_USER_PASSWORD; compose_for_down -f x.yml down)
check "down sem JWT_SECRET nem as senhas do Oracle recebe valores neutros (o Compose não para na interpolação)" \
    test "$out" = "JWT_SECRET=nao-usado-no-down ORACLE_PASSWORD=nao-usado-no-down ORACLE_APP_USER_PASSWORD=nao-usado-no-down"
out=$(JWT_SECRET="valor-real" ORACLE_PASSWORD="oracle-real" ORACLE_APP_USER_PASSWORD="app-real" compose_for_down -f x.yml down)
check "down com os valores exportados usa os exportados" \
    test "$out" = "JWT_SECRET=valor-real ORACLE_PASSWORD=oracle-real ORACLE_APP_USER_PASSWORD=app-real"
unset -f docker

# ----------------------------------------------------------------------------
echo "== backend principal: chave pública e emissor do auth"
app_auth_env
check "AUTH_JWT_PUBLIC_KEY_LOCATION aponta para a publicKey.pem do módulo de autorização" \
    test "${APP_AUTH_ENV[0]}" = "AUTH_JWT_PUBLIC_KEY_LOCATION=$ROOT/backend/vortex-authorization-service/src/main/resources/jwt/publicKey.pem"
check "AUTH_JWT_ISSUER padrão é o emissor padrão do auth" test "${APP_AUTH_ENV[1]}" = "AUTH_JWT_ISSUER=vortex-auth-service"
JWT_ISSUER="emissor-x" app_auth_env
check "AUTH_JWT_ISSUER segue o JWT_ISSUER do auth" test "${APP_AUTH_ENV[1]}" = "AUTH_JWT_ISSUER=emissor-x"

# ----------------------------------------------------------------------------
echo "== PostgreSQL do auth: aviso do volume antigo"
docker() {
    [[ "$1 $2" == "volume inspect" ]] || return 99
    [[ " $MOCK_VOLUMES " == *" $3 "* ]]
}
out=$(MOCK_VOLUMES="docker_auth-db-data" warn_auth_db_legacy_volume)
check "volume novo ainda não existe e o antigo sim: avisa e aponta o script de migração" \
    bash -c '[[ "$1" == *"docker_auth-db-data"* && "$1" == *"scripts/migrar-auth-db-pg18.sh"* ]]' _ "$out"
out=$(MOCK_VOLUMES="docker_auth-db-data vortex-auth-db-data-pg18" warn_auth_db_legacy_volume)
check "volume novo já existe: não repete o aviso" test -z "$out"
out=$(MOCK_VOLUMES="" warn_auth_db_legacy_volume)
check "instalação nova (sem volume antigo): nenhum aviso" test -z "$out"
unset -f docker

# ----------------------------------------------------------------------------
echo "== componentes (-c) e perfis de mensageria"
components_of() {
    (BACKEND_ONLY="" FRONTEND_ONLY="" COMPONENTS="$1" NO_INTERACTION=true choose_components > /dev/null &&
        echo "$RUN_BACKEND $RUN_FRONTEND")
}
check "-c full roda backend e frontend" test "$(components_of full)" = "true true"
check "-c backend roda só o backend" test "$(components_of backend)" = "true false"
check "-c frontend roda só o frontend" test "$(components_of frontend)" = "false true"
out=$(BACKEND_ONLY="" FRONTEND_ONLY="" COMPONENTS="todos" NO_INTERACTION=true choose_components 2>&1)
rc=$?
check "choose_components com valor desconhecido sai com 1, sem cair no padrão completo" test "$rc" -eq 1
check "...e diz o valor recebido e os aceitos, como o main" \
    bash -c '[[ "$1" == *todos* && "$1" == *"$2"* ]]' _ "$out" "Use 'full', 'backend' ou 'frontend'."
check "mensageria none não acrescenta perfil" test "$(MESSAGING_SYSTEM=none RUN_MODE=dev app_spring_profiles prd)" = "prd"
check "mensageria kafka acrescenta o perfil kafka" test "$(MESSAGING_SYSTEM=kafka RUN_MODE=dev app_spring_profiles prd)" = "prd,kafka"

echo ""
echo "Resultado: $PASS ok, $FAIL falha(s)"
[[ $FAIL -eq 0 ]]
