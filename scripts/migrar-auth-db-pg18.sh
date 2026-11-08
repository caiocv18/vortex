#!/bin/bash
# Copia os dados do PostgreSQL do serviço de autorização do volume antigo para o PostgreSQL 18.
#
# Por que existe: o docker-compose.auth.yml trocou o postgres:17 pelo postgres:18.6-alpine num
# volume novo (vortex-auth-db-data-pg18). O volume antigo (docker_auth-db-data) guarda um cluster
# do PostgreSQL 17 na raiz, porque era montado em /var/lib/postgresql/data; a imagem 18 monta em
# /var/lib/postgresql e se recusa a subir em cima dele. Os usuários cadastrados antes ficam lá.
#
# Como migra: copia o volume antigo para um volume temporário (o antigo é montado só leitura e
# nunca é alterado nem apagado), sobe em cima da cópia um PostgreSQL temporário da mesma versão
# do cluster, tira um pg_dump do banco vortex_auth e restaura no PostgreSQL 18 numa transação só
# (se algo falhar no meio, o destino fica como estava). No fim, apaga só o que ele mesmo criou
# (o container temporário e a cópia) e confere o número de usuários dos dois lados.
#
# O volume antigo continua lá para conferir ou voltar atrás. Quando não precisar mais dele:
#   docker volume rm docker_auth-db-data
#
# Uso (com o Vortex parado: ./start-vortex.sh --stop):
#   bash scripts/migrar-auth-db-pg18.sh [opções]
#     --origem VOLUME        volume antigo (padrão docker_auth-db-data)
#     --destino CONTAINER    PostgreSQL 18 que recebe os dados (padrão vortex-auth-db; se ele não
#                            estiver de pé, o script sobe o auth-db do docker-compose.auth.yml e
#                            o para no fim)
#     --imagem-origem IMAGEM imagem do PostgreSQL temporário (padrão: postgres:17, a do compose
#                            antigo, para um cluster 17; a do docker-compose.auth.yml para um 18)
#     --substituir           troca também um destino que já tem usuários (os dele se perdem)
# Sem --substituir, um destino com usuários em auth.users é recusado. Um destino sem usuários
# (só o schema vazio que o Flyway criou na primeira subida do auth) é trocado sem perguntar.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

ORIGEM="docker_auth-db-data"
DESTINO="vortex-auth-db"
DESTINO_PADRAO="vortex-auth-db"
IMAGEM_ORIGEM=""
SUBSTITUIR=false
AUTH_COMPOSE="infra/docker/docker-compose.auth.yml"
DB_USER="vortex_auth"
DB_NAME="vortex_auth"

uso() {
    sed -n '/^# Uso/,/^# (só o schema/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --origem) ORIGEM="$2"; shift 2 ;;
        --destino) DESTINO="$2"; shift 2 ;;
        --imagem-origem) IMAGEM_ORIGEM="$2"; shift 2 ;;
        --substituir) SUBSTITUIR=true; shift ;;
        -h|--help) uso; exit 0 ;;
        *) echo "Opção desconhecida: $1" >&2; uso >&2; exit 2 ;;
    esac
done

info() { printf '%s\n' "$*"; }
falha() { printf '❌ %s\n' "$*" >&2; exit 1; }

# psql no container: $1 container, $2 SQL. Saída sem cabeçalho nem alinhamento.
sql() {
    docker exec "$1" psql -U "$DB_USER" -d "$DB_NAME" -tAq -v ON_ERROR_STOP=1 -c "$2"
}

# Pronto de verdade: pelo TCP do loopback do container. Num volume novo o entrypoint da imagem
# roda o initdb num servidor temporário só no socket Unix e depois o derruba; o pg_isready pelo
# socket responderia "pronto" para esse servidor, que logo em seguida está desligando.
esperar_postgres() {
    local container=$1 i
    for ((i = 0; i < 90; i++)); do
        if docker exec "$container" pg_isready -q -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" 2>/dev/null; then
            return 0
        fi
        if [[ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" != "true" ]]; then
            break
        fi
        sleep 1
    done
    info "Últimas linhas do log de $container:"
    docker logs --tail 20 "$container" 2>&1 || true
    return 1
}

# Usuários em auth.users (0 se a tabela não existe)
contar_usuarios() {
    if [[ "$(sql "$1" "select to_regclass('auth.users') is not null")" == "t" ]]; then
        sql "$1" "select count(*) from auth.users"
    else
        echo 0
    fi
}

command -v docker > /dev/null 2>&1 || falha "Docker não encontrado."
docker info > /dev/null 2>&1 || falha "O Docker não respondeu (docker info)."
docker volume inspect "$ORIGEM" > /dev/null 2>&1 || falha "O volume $ORIGEM não existe: não há nada para migrar."

# Restaurar por baixo de um auth no ar deixaria sessões e caches dele apontando para dados trocados
if [[ "$DESTINO" == "$DESTINO_PADRAO" ]] && command -v ss > /dev/null 2>&1 && ss -ltnH 'sport = :8081' 2>/dev/null | grep -q .; then
    falha "A porta 8081 (serviço de autorização) está em uso. Pare o Vortex antes: ./start-vortex.sh --stop"
fi

IMAGEM_NOVA=$(sed -n 's/^[[:space:]]*image:[[:space:]]*\(postgres:[^[:space:]]*\).*/\1/p' "$AUTH_COMPOSE" | head -1)
IMAGEM_NOVA=${IMAGEM_NOVA:-postgres:18.6-alpine}

SUFIXO="$(date +%s)-$$"
TEMP="vortex-auth-db-migracao-$SUFIXO"
COPIA="vortex-auth-db-migracao-copia-$SUFIXO"
TMPDIR_DUMP=$(mktemp -d)
criou_copia=false
criou_temp=false
subiu_destino=false

limpar() {
    if [[ "$criou_temp" == "true" ]]; then
        docker rm -f -v "$TEMP" > /dev/null 2>&1 || true
    fi
    # Só a cópia que este script criou; o volume de origem nunca é tocado
    if [[ "$criou_copia" == "true" ]]; then
        docker volume rm "$COPIA" > /dev/null 2>&1 || true
    fi
    if [[ "$subiu_destino" == "true" ]]; then
        docker compose -f "$AUTH_COMPOSE" stop auth-db > /dev/null 2>&1 || true
    fi
    rm -rf "$TMPDIR_DUMP"
}
trap limpar EXIT

# 1) Onde está o cluster no volume antigo e de que versão ele é (montado só leitura)
layout=$(docker run --rm --network none -v "$ORIGEM":/origem:ro --entrypoint sh "$IMAGEM_NOVA" -c '
    if [ -f /origem/PG_VERSION ]; then echo ". $(cat /origem/PG_VERSION)"; exit 0; fi
    for d in /origem/data /origem/*/docker; do
        if [ -f "$d/PG_VERSION" ]; then echo "${d#/origem/} $(cat "$d/PG_VERSION")"; exit 0; fi
    done
    echo "nenhum -"')
read -r subdir versao <<< "$layout"
[[ "$subdir" != "nenhum" ]] || falha "O volume $ORIGEM não tem um cluster PostgreSQL (nenhum PG_VERSION)."
[[ "$versao" =~ ^[0-9]+$ ]] || falha "Versão do cluster em $ORIGEM ilegível: '$versao'."
info "📦 Volume de origem $ORIGEM: cluster do PostgreSQL $versao em ${subdir/#./a raiz do volume}"

if [[ -z "$IMAGEM_ORIGEM" ]]; then
    case "$versao" in
        17) IMAGEM_ORIGEM="postgres:17" ;;          # a imagem do docker-compose.auth.yml antigo (Debian)
        18) IMAGEM_ORIGEM="$IMAGEM_NOVA" ;;         # cluster criado pelo compose atual (alpine)
        *) IMAGEM_ORIGEM="postgres:$versao" ;;
    esac
fi

# 2) Destino: o PostgreSQL 18 (sobe o auth-db do compose se for o padrão e estiver parado)
if ! docker ps --format '{{.Names}}' | grep -qx "$DESTINO"; then
    [[ "$DESTINO" == "$DESTINO_PADRAO" ]] || falha "O container $DESTINO não está rodando."
    info "🐘 Subindo o auth-db (PostgreSQL 18, volume vortex-auth-db-data-pg18)..."
    docker network inspect vortex-simple > /dev/null 2>&1 || docker network create vortex-simple > /dev/null
    docker compose -f "$AUTH_COMPOSE" up -d auth-db > /dev/null
    subiu_destino=true
fi
esperar_postgres "$DESTINO" || falha "O PostgreSQL de destino ($DESTINO) não ficou pronto."

if docker inspect -f '{{range .Mounts}}{{.Name}} {{end}}' "$DESTINO" | tr ' ' '\n' | grep -qx "$ORIGEM"; then
    falha "O destino $DESTINO usa o próprio volume $ORIGEM: origem e destino precisam ser volumes diferentes."
fi
versao_destino=$(sql "$DESTINO" "select current_setting('server_version_num')::int / 10000")
(( versao_destino >= versao )) || falha "O destino é o PostgreSQL $versao_destino, mais velho que a origem ($versao)."

usuarios_destino=$(contar_usuarios "$DESTINO")
if (( usuarios_destino > 0 )) && [[ "$SUBSTITUIR" != "true" ]]; then
    falha "O destino já tem $usuarios_destino usuário(s) em auth.users. Para trocá-los pelos do volume antigo (os do destino se perdem), rode de novo com --substituir."
fi

# 3) PostgreSQL temporário da versão da origem, em cima de uma CÓPIA do volume antigo
info "📋 Copiando $ORIGEM para o volume temporário $COPIA (o original fica intacto)..."
docker volume create "$COPIA" > /dev/null
criou_copia=true
docker run --rm --network none -v "$ORIGEM":/origem:ro -v "$COPIA":/copia --entrypoint sh "$IMAGEM_NOVA" \
    -c 'cp -a /origem/. /copia/'

pgdata="/srv/pg"
[[ "$subdir" == "." ]] || pgdata="/srv/pg/$subdir"
info "🐘 Subindo o PostgreSQL $versao temporário ($IMAGEM_ORIGEM) para o pg_dump..."
docker run -d --name "$TEMP" --network none -e PGDATA="$pgdata" -v "$COPIA":/srv/pg "$IMAGEM_ORIGEM" > /dev/null
criou_temp=true
esperar_postgres "$TEMP" || falha "O PostgreSQL temporário não subiu sobre a cópia de $ORIGEM (veja o log acima)."

if [[ "$(sql "$TEMP" "select to_regnamespace('auth') is not null")" != "t" ]]; then
    falha "O banco $DB_NAME de $ORIGEM não tem o schema auth: não há dados do serviço de autorização para migrar."
fi
usuarios_origem=$(contar_usuarios "$TEMP")

# 4) pg_dump para um arquivo (só restaura um dump completo) e restauração numa transação só
dump="$TMPDIR_DUMP/vortex_auth.sql"
info "💾 pg_dump de $DB_NAME ($usuarios_origem usuário(s))..."
docker exec "$TEMP" pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges > "$dump" \
    || falha "O pg_dump falhou; nada foi restaurado e o destino ficou como estava."

info "♻️  Restaurando no PostgreSQL $versao_destino ($DESTINO), numa transação só..."
if ! { echo "DROP SCHEMA IF EXISTS auth CASCADE;"; cat "$dump"; } \
    | docker exec -i "$DESTINO" psql -U "$DB_USER" -d "$DB_NAME" -q -v ON_ERROR_STOP=1 --single-transaction > "$TMPDIR_DUMP/restore.log" 2>&1; then
    cat "$TMPDIR_DUMP/restore.log" >&2
    falha "A restauração falhou e foi desfeita; o destino ficou como estava."
fi

usuarios_depois=$(contar_usuarios "$DESTINO")
if [[ "$usuarios_depois" != "$usuarios_origem" ]]; then
    falha "Conferência: a origem tem $usuarios_origem usuário(s) e o destino ficou com $usuarios_depois."
fi
info "✅ Migração concluída: $usuarios_depois usuário(s) em auth.users no PostgreSQL $versao_destino."
info "   O volume antigo $ORIGEM não foi alterado. Quando não precisar mais dele: docker volume rm $ORIGEM"
