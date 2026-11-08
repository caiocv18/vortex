#!/bin/bash
# Testes do start-vortex.sh que não precisam de Docker, JVM nem rede: carrega as funções do
# launcher (source, sem rodar o main) e confere
#   - a identidade dos pid files (PID reaproveitado, outro boot ou formato antigo nunca recebem sinal);
#   - a validação do cache AOT (só uma recusa explícita da JVM apaga o cache);
#   - as chaves RSA do JWT do auth (gera quando falta, nunca sobrescreve, move do local antigo);
#   - o JWT_SECRET do prd e o valor neutro dos "down";
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
    local bin=${1:-sleep} pid
    pid=$( ("$bin" 300 > /dev/null 2>&1 & echo $!) )
    SPAWNED+=("$pid")
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
stop_pid_file "$WORK/a.pid" "teste" "sleep" > /dev/null
check "stop_pid_file encerra o processo que confere" wait_gone "$p"
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

# docker falso: imprime o JWT_SECRET que o comando recebeu
docker() { echo "JWT_SECRET=${JWT_SECRET:-}"; }
out=$(unset JWT_SECRET; compose_for_down -f x.yml down)
check "down sem JWT_SECRET recebe um valor neutro (o Compose não para na interpolação)" \
    test "$out" = "JWT_SECRET=nao-usado-no-down"
out=$(JWT_SECRET="valor-real" compose_for_down -f x.yml down)
check "down com JWT_SECRET usa o valor exportado" test "$out" = "JWT_SECRET=valor-real"
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

echo ""
echo "Resultado: $PASS ok, $FAIL falha(s)"
[[ $FAIL -eq 0 ]]
