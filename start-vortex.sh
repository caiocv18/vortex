#!/bin/bash

# Script de inicialização completo do Vortex
# Gerencia os backends (Spring Boot e Quarkus), os frontends (Vue.js e React) e a infraestrutura Docker
# Permite escolher o ambiente (dev com H2, prd com Oracle) e o modo de execução:
#   leve (padrão): jars com JVM enxuta e cache AOT, frontends compilados servidos por um nginx só
#   dev:           hot reload (spring-boot:run, quarkus:dev e Vite), com teto de memória nas JVMs
#
# CORREÇÕES IMPLEMENTADAS:
# - Kafka agora é parado corretamente em todas as configurações
# - Função melhorada para aguardar Kafka estar completamente pronto
# - Backend em produção agora verifica se Kafka está pronto antes de iniciar
# - Parada de serviços mais robusta com verificação de containers órfãos
# - Tempos de espera aumentados para inicialização mais confiável
# - Banner do terminal corrigido
# - Gerenciamento automático de redes Docker com recuperação inteligente
# - Tratamento robusto de erros de rede no docker compose
# - Monitoramento opcional de redes Docker em tempo real
# - Modo leve (--modo leve, padrão) e modo dev (--modo dev), com as flags de JVM num lugar só
# - Build só do que mudou (carimbo por artefato) e cache AOT das duas JVMs (JEP 514/515)
# - Docker Compose v2 ("docker compose") em todo lugar; Kafka nativo em KRaft, sem ZooKeeper
# - Parada por pid file: TERM, até 15 s de espera e só então KILL (nada de pkill por padrão)
# - Java (SDKMAN) e Node.js (nvm) carregados pelo próprio launcher

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ================================================================
# CONFIGURAÇÃO (fonte única)
# ================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAVA_SDK_VERSION="27.0.1-tem"
JAVA_MAJOR_REQUIRED="27"
NODE_MAJOR_REQUIRED="26"

# Flags de JVM: as MESMAS no treino do cache AOT e na execução (o cache só vale com flags iguais).
# - SerialGC explícito: no JDK 27 a ergonomia escolhe G1 mesmo em máquina pequena (JEP 523)
# - cabeçalhos de objeto compactos já vêm ligados por padrão no JDK 27 (JEP 534)
# - sem -XX:TieredStopAtLevel=1 no modo leve: jogaria fora o perfil de métodos que o cache AOT
#   guarda do treino (JEP 515)
JVM_BASE_FLAGS="-XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m"
APP_JVM_FLAGS="$JVM_BASE_FLAGS -Xmx320m"
AUTH_JVM_FLAGS="$JVM_BASE_FLAGS -Xmx224m"
# Modo dev (hot reload): o Spring ganha só o C1, que sobe mais rápido e gasta menos
# (o quarkus:dev já faz isso sozinho)
APP_DEV_JVM_FLAGS="$APP_JVM_FLAGS -XX:TieredStopAtLevel=1"
AUTH_DEV_JVM_FLAGS="$AUTH_JVM_FLAGS"
# JVM do próprio Maven no modo dev: passada só a cada processo, nunca exportada
# (vem depois do .mvn/jvm.config dos módulos, então o -Xmx daqui é o que vale)
DEV_MAVEN_OPTS="-Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
# Menos arenas do malloc da glibc: corta RSS de JVMs e do Node com muitas threads
export MALLOC_ARENA_MAX=2

# Espera do TERM antes do KILL ao parar processos (segundos)
STOP_TIMEOUT=15

APP_BACKEND_DIR="backend/vortex-application-service"
AUTH_BACKEND_DIR="backend/vortex-authorization-service"
APP_FRONTEND_DIR="frontend/vortex-application-service"
AUTH_FRONTEND_DIR="frontend/vortex-authorization-service"
LEVE_COMPOSE="infra/docker/docker-compose.leve.yml"

# URLs que entram no build dos frontends: o navegador chama os backends direto
FRONT_VITE_API_BASE_URL="http://localhost:8080/api"
FRONT_VITE_AUTH_BACKEND_URL="http://localhost:8081"
FRONT_VITE_AUTH_SERVICE_URL="http://localhost:3001"
FRONT_VITE_API_URL="http://localhost:8081"

# Função para imprimir com cores
print_color() {
    echo -e "${1}${2}${NC}"
}

# Função para mostrar banner
show_banner() {
    print_color $CYAN "╔═══════════════════════════════════════════════════════════════╗"
    print_color $CYAN "║                                                               ║"
    print_color $CYAN "║                    🚀 VORTEX LAUNCHER 🚀                      ║"
    print_color $CYAN "║                                                               ║"
    print_color $CYAN "║              Sistema de Controle de Estoque                   ║"
    print_color $CYAN "║                                                               ║"
    print_color $CYAN "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
}

# Função para mostrar ajuda
show_help() {
    echo "Uso: $0 [OPÇÕES]"
    echo ""
    echo "Opções:"
    echo "  -h, --help          Mostrar esta ajuda"
    echo "  -e, --env ENV       Definir ambiente (dev|prd)"
    echo "  -r, --modo MODO     Definir modo de execução (leve|dev), padrão leve"
    echo "  -b, --backend-only  Executar apenas backend"
    echo "  -f, --frontend-only Executar apenas frontend"
    echo "  -c, --components C  Definir componentes (full|backend|frontend)"
    echo "  -m, --messaging MSG Definir sistema de mensageria (kafka|rabbitmq|sqs|none)"
    echo "  --stop              Parar todos os serviços"
    echo "  --clean             Limpar containers e volumes"
    echo "  --fix-kafka         Executar correção automática do Kafka"
    echo "  --fix-networks      Limpar e recriar redes Docker problemáticas"
    echo "  --logs              Mostrar logs após iniciar"
    echo "  --no-interaction    Modo não interativo (usa valores padrão)"
    echo "  --monitor-networks  Monitorar e recuperar redes Docker automaticamente"
    echo ""
    echo "Ambientes:"
    echo "  dev  - Desenvolvimento com H2 Database"
    echo "  prd  - Produção com Oracle Database (backend principal no Docker)"
    echo ""
    echo "Modos de execução:"
    echo "  leve - Padrão. Builds só do que mudou, jars com JVM enxuta e cache AOT,"
    echo "         frontends compilados servidos por um nginx (vortex-static). Menor uso de RAM."
    echo "  dev  - Hot reload: spring-boot:run, quarkus:dev e Vite dev server, com teto de"
    echo "         memória nas JVMs filhas. Gasta mais RAM."
    echo ""
    echo "Sistemas de Mensageria:"
    echo "  kafka    - Apache Kafka para event streaming"
    echo "  rabbitmq - RabbitMQ para mensageria tradicional"
    echo "  sqs      - Amazon SQS para processamento assíncrono"
    echo "  none     - Sem sistema de mensageria"
    echo ""
    echo "Componentes:"
    echo "  full     - Backend + Frontend (padrão)"
    echo "  backend  - Apenas Backend"
    echo "  frontend - Apenas Frontend"
    echo ""
    echo "Exemplos:"
    echo "  $0                                    # Modo interativo"
    echo "  $0 --modo leve -e dev -m none --no-interaction  # Mais leve possível"
    echo "  $0 -e dev -r dev -m none              # Hot reload (modo dev)"
    echo "  $0 -e dev -m kafka -c full           # Desenvolvimento com Kafka (completo)"
    echo "  $0 -e dev -m rabbitmq -c backend     # Desenvolvimento com RabbitMQ (só backend)"
    echo "  $0 -e prd -m kafka -c full --logs    # Produção com Kafka"
    echo "  $0 -e dev -m none -c backend         # Desenvolvimento sem mensageria"
    echo "  $0 --backend-only -m sqs             # Apenas backend com SQS"
    echo "  $0 --stop                            # Parar serviços"
    echo "  $0 --fix-kafka                       # Corrigir problemas do Kafka"
    echo "  $0 --fix-networks                    # Corrigir redes Docker"
    echo "  $0 --no-interaction -e dev -m kafka  # Modo não interativo"
    echo "  $0 --monitor-networks                # Monitorar redes Docker"
}

# ================================================================
# UTILITÁRIOS
# ================================================================

now_ms() {
    date +%s%3N
}

fmt_secs() {
    awk -v ms="$1" 'BEGIN { printf "%.1f", ms / 1000 }'
}

java_version_line() {
    java -version 2>&1 | head -1
}

java_major_version() {
    java -version 2>&1 | awk -F'"' '/version/ { split($2, v, "."); print v[1]; exit }'
}

http_status() {
    curl -s -o /dev/null -m 3 -w '%{http_code}' "$1" 2>/dev/null || true
}

http_ok() {
    [[ "$(http_status "$1")" == "200" ]]
}

# Espera um GET devolver 200. $1 url, $2 timeout em segundos, $3 PID opcional (falha cedo se o
# processo morrer). Deixa o tempo de espera em WAIT_ELAPSED_MS. Retorno: 0 ok, 1 timeout, 2 morreu.
WAIT_ELAPSED_MS=0
wait_for_http() {
    local url=$1 timeout=${2:-120} pid=${3:-}
    local t0 code
    t0=$(now_ms)
    WAIT_ELAPSED_MS=0
    while true; do
        code=$(http_status "$url")
        if [[ "$code" == "200" ]]; then
            WAIT_ELAPSED_MS=$(( $(now_ms) - t0 ))
            return 0
        fi
        if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
            WAIT_ELAPSED_MS=$(( $(now_ms) - t0 ))
            return 2
        fi
        if (( $(now_ms) - t0 >= timeout * 1000 )); then
            WAIT_ELAPSED_MS=$(( $(now_ms) - t0 ))
            return 1
        fi
        sleep 0.25
    done
}

# Espera um processo terminar. $1 PID, $2 timeout em segundos. Retorna 1 se ainda estiver vivo.
wait_pid_exit() {
    local pid=$1 timeout=${2:-$STOP_TIMEOUT} i
    for ((i = 0; i < timeout * 4; i++)); do
        kill -0 "$pid" 2>/dev/null || return 0
        sleep 0.25
    done
    ! kill -0 "$pid" 2>/dev/null
}

# Para o processo de um pid file: confere se o PID ainda é o processo esperado (o padrão casa
# com a linha de comando), manda TERM, espera até STOP_TIMEOUT segundos e só então KILL.
stop_pid_file() {
    local file=$1 label=$2 pattern=${3:-}
    [[ -f "$file" ]] || return 0
    local pid cmd
    pid=$(tr -dc '0-9' < "$file")
    if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
        rm -f "$file"
        return 0
    fi
    cmd=$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
    if [[ -n "$pattern" ]] && ! grep -Eq "$pattern" <<< "$cmd"; then
        print_color $YELLOW "   ⚠️  $file aponta para o PID $pid, que não é mais deste launcher; ignorado."
        rm -f "$file"
        return 0
    fi
    print_color $BLUE "   ⏹️  $label (PID $pid): TERM..."
    kill -TERM "$pid" 2>/dev/null || true
    if ! wait_pid_exit "$pid" "$STOP_TIMEOUT"; then
        print_color $YELLOW "   ⚠️  $label não saiu em ${STOP_TIMEOUT}s: KILL"
        kill -KILL "$pid" 2>/dev/null || true
        wait_pid_exit "$pid" 5 || true
    fi
    rm -f "$file"
}

# Portas: a que está com container Docker nunca é mexida aqui (quem cuida é o docker compose)
port_held_by_container() {
    docker ps --format '{{.Ports}}' 2>/dev/null | grep -q ":${1}->"
}

container_on_port() {
    docker ps --format '{{.Names}} {{.Ports}}' 2>/dev/null | grep ":${1}->" | awk '{print $1}' | head -1
}

listen_pids() {
    lsof -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | sort -u | tr '\n' ' '
}

# TERM nos PIDs, até 10 s de espera, KILL no que sobrar
terminate_pids() {
    local pids=("$@") pid i alive
    [[ ${#pids[@]} -eq 0 ]] && return 0
    kill -TERM "${pids[@]}" 2>/dev/null || true
    for ((i = 0; i < 40; i++)); do
        alive=0
        for pid in "${pids[@]}"; do
            kill -0 "$pid" 2>/dev/null && alive=1
        done
        [[ $alive -eq 0 ]] && return 0
        sleep 0.25
    done
    kill -KILL "${pids[@]}" 2>/dev/null || true
}

# Garante a porta livre para um processo do launcher. $1 porta, $2 rótulo.
ensure_port_free() {
    local port=$1 label=$2 pids
    if port_held_by_container "$port"; then
        print_color $RED "❌ Porta $port ($label) está com o container $(container_on_port "$port")."
        return 1
    fi
    pids=$(listen_pids "$port")
    if [[ -n "$pids" ]]; then
        print_color $YELLOW "⚠️  Porta $port ($label) ocupada pelo PID ${pids% }. Liberando conforme PORTS.md (TERM, depois KILL)..."
        # shellcheck disable=SC2086
        terminate_pids $pids
    fi
    if ss -ltnH "sport = :$port" 2>/dev/null | grep -q .; then
        print_color $RED "❌ Porta $port ($label) continua ocupada (processo de outro usuário?). Veja: sudo ss -ltnp 'sport = :$port'"
        return 1
    fi
    return 0
}

# Registra o tempo de subida de uma JVM em logs/startup-times.log e mostra na tela
record_startup_time() {
    local svc=$1 cache=$2 ms=$3 log=$4 jvm_time=""
    if [[ -f "$log" ]]; then
        jvm_time=$(grep -m1 -oE "Started [A-Za-z]+ in [0-9.]+ seconds|started in [0-9.]+s" "$log" | grep -oE "[0-9.]+" | head -1 || true)
    fi
    echo "$(date '+%Y-%m-%dT%H:%M:%S') modo=$RUN_MODE servico=$svc cache_aot=$cache ate_health=$(fmt_secs "$ms")s jvm_reportado=${jvm_time:-?}s" >> logs/startup-times.log
    print_color $GREEN "   ⏱️  $svc pronto em $(fmt_secs "$ms")s até o health 200 (JVM: ${jvm_time:-?}s; cache AOT: $cache)"
}

# ================================================================
# PRÉ-REQUISITOS
# ================================================================

# Java 27 (SDKMAN) e Node.js 26 (nvm). O .bashrc retorna cedo em shell não interativo,
# então o launcher carrega os dois por conta própria.
load_toolchain() {
    print_color $BLUE "🧰 Carregando Java $JAVA_SDK_VERSION (SDKMAN) e Node.js $NODE_MAJOR_REQUIRED (nvm)..."

    local sdkman_init="${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"
    if [[ ! -s "$sdkman_init" ]]; then
        print_color $RED "❌ SDKMAN não encontrado ($sdkman_init)."
        print_color $YELLOW "💡 Instale o SDKMAN (https://sdkman.io) e rode: sdk install java $JAVA_SDK_VERSION"
        exit 1
    fi
    # Os scripts do SDKMAN e do nvm não suportam set -e: rodam com ele desligado
    local sdk_rc=0
    set +e
    # shellcheck disable=SC1090
    source "$sdkman_init"
    sdk use java "$JAVA_SDK_VERSION" >/dev/null 2>&1
    sdk_rc=$?
    set -e
    local java_major
    java_major=$(java_major_version 2>/dev/null || true)
    if [[ $sdk_rc -ne 0 || "$java_major" != "$JAVA_MAJOR_REQUIRED" ]]; then
        print_color $RED "❌ Java $JAVA_SDK_VERSION não disponível pelo SDKMAN (java ativo: ${java_major:-nenhum})."
        print_color $YELLOW "💡 Rode: sdk install java $JAVA_SDK_VERSION"
        exit 1
    fi
    print_color $GREEN "✅ Java: $(java_version_line)"

    export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
    if [[ ! -s "$NVM_DIR/nvm.sh" ]]; then
        print_color $RED "❌ nvm não encontrado ($NVM_DIR/nvm.sh)."
        print_color $YELLOW "💡 Instale o nvm (https://github.com/nvm-sh/nvm) e rode: nvm install $NODE_MAJOR_REQUIRED"
        exit 1
    fi
    local nvm_rc=0
    set +e
    # shellcheck disable=SC1091
    . "$NVM_DIR/nvm.sh" --no-use
    nvm use "$NODE_MAJOR_REQUIRED" >/dev/null 2>&1
    nvm_rc=$?
    set -e
    local node_version
    node_version=$(node --version 2>/dev/null || true)
    if [[ $nvm_rc -ne 0 || "$node_version" != v${NODE_MAJOR_REQUIRED}.* ]]; then
        print_color $RED "❌ Node.js $NODE_MAJOR_REQUIRED não disponível pelo nvm (node ativo: ${node_version:-nenhum})."
        print_color $YELLOW "💡 Rode: nvm install $NODE_MAJOR_REQUIRED"
        exit 1
    fi
    NODE_AVAILABLE=true
    NODE_VERSION="$node_version"
    NPM_AVAILABLE=true
    NPM_VERSION=$(npm --version 2>/dev/null || echo "?")
    print_color $GREEN "✅ Node.js encontrado: $NODE_VERSION (npm $NPM_VERSION)"
}

# O Docker Engine desta distro WSL sobe no boot (o [boot] command do /etc/wsl.conf) e pode levar
# alguns segundos depois que o primeiro terminal abre: espera até 60 s antes de desistir.
wait_for_docker() {
    if docker info > /dev/null 2>&1; then
        return 0
    fi
    print_color $YELLOW "⏳ Docker ainda não respondeu. Nesta distro WSL o Docker Engine sobe no boot da distro e pode levar alguns segundos; aguardando até 60 s..."
    local i
    for ((i = 1; i <= 60; i++)); do
        if [[ -S /var/run/docker.sock ]] && docker info > /dev/null 2>&1; then
            print_color $GREEN "✅ Docker respondeu após ${i}s"
            return 0
        fi
        sleep 1
    done
    if [[ -S /var/run/docker.sock ]]; then
        print_color $RED "❌ O socket /var/run/docker.sock existe, mas o Docker não respondeu em 60 s."
    else
        print_color $RED "❌ O Docker não subiu em 60 s (/var/run/docker.sock não existe)."
    fi
    print_color $YELLOW "💡 Verifique o serviço (sudo service docker status) e o [boot] command em /etc/wsl.conf."
    exit 1
}

# Função para verificar pré-requisitos
check_prerequisites() {
    print_color $BLUE "🔍 Verificando pré-requisitos..."

    # Verificar se diretórios dos serviços existem
    local d
    for d in "$APP_BACKEND_DIR" "$AUTH_BACKEND_DIR" "$APP_FRONTEND_DIR" "$AUTH_FRONTEND_DIR"; do
        if [[ ! -d "$d" ]]; then
            print_color $RED "❌ Diretório $d não encontrado."
            exit 1
        fi
    done

    # Verificar Docker
    if ! command -v docker &> /dev/null; then
        print_color $RED "❌ Docker não encontrado. Por favor, instale o Docker."
        exit 1
    fi
    wait_for_docker

    # Verificar Docker Compose v2 (plugin "docker compose")
    if ! docker compose version > /dev/null 2>&1; then
        print_color $RED "❌ Docker Compose v2 não encontrado (docker compose). Instale o plugin docker-compose-plugin."
        exit 1
    fi

    # Verificar conflitos de porta usando o script dedicado (pula portas de containers)
    if [[ -f "scripts/check-ports.sh" ]]; then
        print_color $BLUE "🚢 Verificando conflitos de porta conforme PORTS.md..."
        chmod +x scripts/check-ports.sh
        ./scripts/check-ports.sh --auto-kill 2>/dev/null || true
    fi

    # Verificar se há problemas conhecidos do Kafka
    if [[ -f "logs/backend.log" ]] && grep -q "kafka:[0-9]*.*DNS resolution failed" logs/backend.log 2>/dev/null; then
        print_color $YELLOW "⚠️  Detectado problema conhecido do Kafka (DNS resolution)"
        print_color $YELLOW "💡 Execute: ./start-vortex.sh --fix-kafka"
    fi

    print_color $GREEN "✅ Docker encontrado e rodando ($(docker compose version --short 2>/dev/null || echo 'compose v2'))"
}

# ================================================================
# REDES DOCKER
# ================================================================

# Função para garantir que as redes Docker necessárias existam
ensure_docker_networks() {
    print_color $BLUE "🌐 Verificando e criando redes Docker necessárias..."

    # Lista de redes necessárias (declaradas como external nos compose que as usam)
    local networks=("vortex-simple" "vortex-kafka-network" "vortex-rabbitmq-network")

    for network in "${networks[@]}"; do
        if ! docker network ls --format "{{.Name}}" | grep -q "^${network}$"; then
            print_color $YELLOW "📡 Criando rede Docker: $network"
            # Criar rede com configurações específicas para evitar conflitos IPv4/IPv6
            docker network create \
                --driver bridge \
                --opt com.docker.network.enable_ipv6=false \
                --opt com.docker.network.bridge.enable_ip_masquerade=true \
                "$network" >/dev/null 2>&1 || true
        else
            print_color $GREEN "✅ Rede Docker $network já existe"
        fi
    done

    print_color $GREEN "✅ Todas as redes Docker necessárias estão disponíveis"
}

# Função para validar se todas as redes necessárias existem
validate_docker_networks() {
    local missing_networks=()
    local networks=("vortex-simple" "vortex-kafka-network" "vortex-rabbitmq-network")

    for network in "${networks[@]}"; do
        if ! docker network ls --format "{{.Name}}" | grep -q "^${network}$"; then
            missing_networks+=("$network")
        fi
    done

    if [[ ${#missing_networks[@]} -gt 0 ]]; then
        print_color $YELLOW "⚠️  Redes Docker faltando: ${missing_networks[*]}"
        return 1
    fi

    return 0
}

# Função para executar docker compose com tratamento automático de erro de rede
safe_docker_compose() {
    local compose_file="$1"
    local compose_action="$2"
    local additional_args="${3:-}"
    local max_retries=3
    local retry_count=0
    local error_output

    # Validar redes antes de executar
    if ! validate_docker_networks; then
        print_color $BLUE "🔧 Recriando redes Docker antes de executar docker compose..."
        ensure_docker_networks
    fi

    print_color $BLUE "   🐳 docker compose -f $compose_file $compose_action $additional_args (a primeira vez pode baixar imagens)"
    while [[ $retry_count -lt $max_retries ]]; do
        # shellcheck disable=SC2086
        if error_output=$(docker compose -f "$compose_file" $compose_action $additional_args 2>&1); then
            return 0
        fi

        # Verificar se o erro é relacionado a rede
        if echo "$error_output" | grep -q "declared as external, but could not be found\|needs to be recreated.*option.*has changed\|was found but has incorrect label"; then
            local missing_network
            missing_network=$(echo "$error_output" | grep -o "network [^ ]* declared as external\|network [^ ]* was found\|Network \"[^\"]*\"" | head -1 | awk '{print $2}' | tr -d '"')
            print_color $YELLOW "⚠️  Problema com rede '$missing_network'. Recriando todas as redes..."

            # Forçar remoção e recriação de todas as redes
            print_color $BLUE "🧹 Removendo redes conflitantes..."
            docker network rm vortex-simple vortex-kafka-network vortex-rabbitmq-network 2>/dev/null || true
            sleep 2

            # Recriar todas as redes necessárias
            ensure_docker_networks

            retry_count=$((retry_count + 1))
            print_color $BLUE "🔄 Tentativa $retry_count de $max_retries para executar docker compose..."
            sleep 2
        else
            # Se não for erro de rede, falhar imediatamente
            print_color $RED "❌ Erro ao executar docker compose:"
            echo "$error_output"
            return 1
        fi
    done

    print_color $RED "❌ Falha ao executar docker compose após $max_retries tentativas"
    return 1
}

# "down" de uma stack, SEM -v (volumes ficam), incluindo os serviços atrás de profiles
compose_down() {
    [[ -f "$1" ]] || return 0
    docker compose -f "$1" --profile '*' down > /dev/null 2>&1 || true
}

# Stack prd (backend + Oracle): docker-compose.yml + override, como um "docker compose" sem -f
prd_compose() {
    docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml "$@"
}

# Função para monitorar e recuperar redes Docker automaticamente
monitor_docker_networks() {
    local check_interval=30  # Verificar a cada 30 segundos

    while true; do
        if ! validate_docker_networks >/dev/null 2>&1; then
            print_color $YELLOW "🔧 Detectada ausência de redes Docker. Recuperando automaticamente..."
            ensure_docker_networks
        fi
        sleep $check_interval
    done
}

# ================================================================
# ESCOLHAS (menus interativos)
# ================================================================

# Função para escolher cor do tema
choose_color_theme() {
    if [[ -n "$COLOR_THEME" ]]; then
        return
    fi

    # Modo não interativo usa verde por padrão (tema original)
    if [[ "$NO_INTERACTION" == "true" ]]; then
        COLOR_THEME="verde"
        print_color $GREEN "✅ Tema verde selecionado (padrão)"
        return
    fi

    print_color $CYAN "
🎨 ESCOLHA A COR DO TEMA DA APLICAÇÃO:

1) 🔴 Vermelho     2) 🟢 Verde        3) 🔵 Azul         4) 🟠 Laranja
5) 🟣 Roxo         6) 🩷 Rosa         7) 🔷 Azul Claro   8) 🔹 Azul Escuro
9) 🟡 Amarelo     10) ⚫ Cinza       11) ⚫ Preto       12) ⚪ Branco
"

    while true; do
        read -p "Digite sua escolha (1-12): " choice
        case $choice in
            1)
                COLOR_THEME="vermelho"
                print_color $RED "✅ Tema vermelho selecionado"
                break
                ;;
            2)
                COLOR_THEME="verde"
                print_color $GREEN "✅ Tema verde selecionado"
                break
                ;;
            3)
                COLOR_THEME="azul"
                print_color $BLUE "✅ Tema azul selecionado"
                break
                ;;
            4)
                COLOR_THEME="laranja"
                print_color $YELLOW "✅ Tema laranja selecionado"
                break
                ;;
            5)
                COLOR_THEME="roxo"
                print_color $PURPLE "✅ Tema roxo selecionado"
                break
                ;;
            6)
                COLOR_THEME="rosa"
                print_color $PURPLE "✅ Tema rosa selecionado"
                break
                ;;
            7)
                COLOR_THEME="azulClaro"
                print_color $CYAN "✅ Tema azul claro selecionado"
                break
                ;;
            8)
                COLOR_THEME="azulEscuro"
                print_color $BLUE "✅ Tema azul escuro selecionado"
                break
                ;;
            9)
                COLOR_THEME="amarelo"
                print_color $YELLOW "✅ Tema amarelo selecionado"
                break
                ;;
            10)
                COLOR_THEME="cinza"
                print_color $NC "✅ Tema cinza selecionado"
                break
                ;;
            11)
                COLOR_THEME="preto"
                print_color $NC "✅ Tema preto selecionado"
                break
                ;;
            12)
                COLOR_THEME="branco"
                print_color $NC "✅ Tema branco selecionado"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite um número de 1 a 12."
                ;;
        esac
    done
}

# Função para escolher ambiente
choose_environment() {
    if [[ -n "$ENVIRONMENT" ]]; then
        return
    fi

    # Modo não interativo usa desenvolvimento por padrão
    if [[ "$NO_INTERACTION" == "true" ]]; then
        ENVIRONMENT="dev"
        print_color $GREEN "✅ Ambiente de desenvolvimento selecionado (padrão)"
        return
    fi

    print_color $YELLOW "
🌍 Escolha o ambiente de execução:

1) 🔧 Desenvolvimento (dev)
   - Backend: Spring Boot
   - Database: H2 (em memória)
   - Frontend: nginx estático (modo leve) ou Vite dev server (modo dev)

2) 🚀 Produção (prd)
   - Backend: Spring Boot
   - Database: Oracle Free (Docker)
   - Frontend: Build otimizado
   - Ambiente containerizado
"

    while true; do
        read -p "Digite sua escolha (1 para dev, 2 para prd): " choice
        case $choice in
            1)
                ENVIRONMENT="dev"
                print_color $GREEN "✅ Ambiente de desenvolvimento selecionado"
                break
                ;;
            2)
                ENVIRONMENT="prd"
                print_color $GREEN "✅ Ambiente de produção selecionado"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite 1 ou 2."
                ;;
        esac
    done
}

# Função para escolher o modo de execução (logo depois do ambiente)
choose_mode() {
    if [[ -n "$RUN_MODE" ]]; then
        print_color $GREEN "✅ Modo $RUN_MODE selecionado"
        return
    fi

    # Modo não interativo usa o leve por padrão
    if [[ "$NO_INTERACTION" == "true" ]]; then
        RUN_MODE="leve"
        print_color $GREEN "✅ Modo leve selecionado (padrão)"
        return
    fi

    print_color $YELLOW "
⚙️  Escolha o modo de execução:

1) 🪶 Leve (padrão, recomendado com pouca RAM)
   - Build só do que mudou
   - Backends como jar, JVM enxuta e cache AOT (sobe mais rápido a partir da 2ª vez)
   - Frontends compilados, servidos por um nginx só (vortex-static)

2) 🔥 Dev (hot reload)
   - spring-boot:run, quarkus:dev e Vite dev server
   - JVMs com teto de memória, mas gasta bem mais RAM que o leve
"

    while true; do
        read -p "Digite sua escolha (1 para leve, 2 para dev) [1]: " choice
        case ${choice:-1} in
            1)
                RUN_MODE="leve"
                print_color $GREEN "✅ Modo leve selecionado"
                break
                ;;
            2)
                RUN_MODE="dev"
                print_color $GREEN "✅ Modo dev selecionado"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite 1 ou 2."
                ;;
        esac
    done
}

# Função para escolher sistema de mensageria
choose_messaging() {
    if [[ -n "$MESSAGING_SYSTEM" ]]; then
        return
    fi

    # Modo não interativo usa Kafka por padrão
    if [[ "$NO_INTERACTION" == "true" ]]; then
        MESSAGING_SYSTEM="kafka"
        print_color $GREEN "✅ Apache Kafka selecionado (padrão)"
        return
    fi

    print_color $YELLOW "
📨 Escolha o sistema de mensageria:

1) 🚀 Apache Kafka
   - Event Sourcing
   - Streaming em tempo real
   - Alta performance
   - Replay de eventos

2) 🐰 RabbitMQ
   - Mensageria tradicional
   - Filas confiáveis
   - Protocolo AMQP
   - Interface de gerenciamento

3) ☁️  Amazon SQS
   - Processamento assíncrono
   - Gerenciado pela AWS
   - Simplicidade de uso
   - Pay-per-use

4) ❌ Nenhum
   - Processamento síncrono apenas
   - Modo simplificado
"

    while true; do
        read -p "Digite sua escolha (1, 2, 3 ou 4): " choice
        case $choice in
            1)
                MESSAGING_SYSTEM="kafka"
                print_color $GREEN "✅ Apache Kafka selecionado"
                break
                ;;
            2)
                MESSAGING_SYSTEM="rabbitmq"
                print_color $GREEN "✅ RabbitMQ selecionado"
                break
                ;;
            3)
                MESSAGING_SYSTEM="sqs"
                print_color $GREEN "✅ Amazon SQS selecionado"
                break
                ;;
            4)
                MESSAGING_SYSTEM="none"
                print_color $GREEN "✅ Nenhum sistema de mensageria selecionado"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite 1, 2, 3 ou 4."
                ;;
        esac
    done
}

# Função para escolher componentes
choose_components() {
    if [[ "$BACKEND_ONLY" == "true" ]]; then
        RUN_BACKEND=true
        RUN_FRONTEND=false
        return
    fi

    if [[ "$FRONTEND_ONLY" == "true" ]]; then
        RUN_BACKEND=false
        RUN_FRONTEND=true
        return
    fi

    # Usar parâmetro -c se fornecido
    if [[ -n "$COMPONENTS" ]]; then
        case $COMPONENTS in
            "full")
                RUN_BACKEND=true
                RUN_FRONTEND=true
                print_color $GREEN "✅ Executando Backend + Frontend"
                return
                ;;
            "backend")
                RUN_BACKEND=true
                RUN_FRONTEND=false
                print_color $GREEN "✅ Executando apenas Backend"
                return
                ;;
            "frontend")
                RUN_BACKEND=false
                RUN_FRONTEND=true
                print_color $GREEN "✅ Executando apenas Frontend"
                return
                ;;
        esac
    fi

    # Modo não interativo usa completo por padrão
    if [[ "$NO_INTERACTION" == "true" ]]; then
        RUN_BACKEND=true
        RUN_FRONTEND=true
        print_color $GREEN "✅ Executando Backend + Frontend (padrão)"
        return
    fi

    print_color $YELLOW "
📦 Escolha os componentes para executar:

1) 🔄 Completo (Backend + Frontend)
2) ⚙️  Apenas Backend
3) 🎨 Apenas Frontend
"

    while true; do
        read -p "Digite sua escolha (1, 2 ou 3): " choice
        case $choice in
            1)
                RUN_BACKEND=true
                RUN_FRONTEND=true
                print_color $GREEN "✅ Executando Backend + Frontend"
                break
                ;;
            2)
                RUN_BACKEND=true
                RUN_FRONTEND=false
                print_color $GREEN "✅ Executando apenas Backend"
                break
                ;;
            3)
                RUN_BACKEND=false
                RUN_FRONTEND=true
                print_color $GREEN "✅ Executando apenas Frontend"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite 1, 2 ou 3."
                ;;
        esac
    done
}

# ================================================================
# PARADA, CORREÇÃO E LIMPEZA
# ================================================================

# Função para parar serviços
stop_services() {
    print_color $YELLOW "🛑 Parando todos os serviços..."

    # 1. Processos iniciados pelo launcher (pid files em logs/): TERM, até 15 s, depois KILL.
    #    Frontends primeiro, depois as JVMs (inclusive treinos do cache AOT) e por fim os
    #    wrappers do Maven do modo dev. Nada de pkill por padrão: "vite" pegaria vitest e
    #    Playwright, e "kafka" pegaria o próprio backend com o perfil kafka.
    stop_pid_file "logs/frontend.pid" "Frontend principal" "vite|node|npm"
    stop_pid_file "logs/auth-frontend.pid" "Frontend de autorização" "vite|node|npm"
    stop_pid_file "logs/aot-train-backend.pid" "Treino do cache AOT do backend" "java"
    stop_pid_file "logs/aot-train-auth.pid" "Treino do cache AOT do auth" "java"
    stop_pid_file "logs/backend-jvm.pid" "JVM do backend (spring-boot:run)" "java"
    stop_pid_file "logs/auth-backend-jvm.pid" "JVM do auth (quarkus:dev)" "java"
    stop_pid_file "logs/backend.pid" "Backend principal" "java|mvnw|maven"
    stop_pid_file "logs/auth-backend.pid" "Backend de autorização" "java|mvnw|maven"

    # 2. Monitoramento de redes, se estiver rodando
    if [[ -f "logs/network-monitor.pid" ]]; then
        stop_pid_file "logs/network-monitor.pid" "Monitoramento de redes Docker" "start-vortex|bash"
        print_color $GREEN "✅ Monitoramento de redes Docker parado"
    fi

    # 3. Stacks Docker Compose, sempre "down" SEM -v: os volumes (ex.: dados do PostgreSQL) ficam
    compose_down "$LEVE_COMPOSE"
    compose_down infra/docker/docker-compose.full.yml
    compose_down infra/docker/docker-compose.full-kafka.yml
    compose_down infra/docker/docker-compose.full-rabbitmq.yml
    prd_compose down > /dev/null 2>&1 || true
    compose_down infra/docker/docker-compose.auth.yml
    compose_down infra/docker/docker-compose.kafka-simple.yml
    compose_down infra/docker/docker-compose.kafka.yml
    compose_down infra/docker/docker-compose.rabbitmq.yml

    # 4. Containers com nomes de versões anteriores (ZooKeeper, backend dev em Docker...)
    local legacy=(vortex-zookeeper-simple vortex-zookeeper vortex-app-dev vortex-auth-service vortex-auth-frontend)
    docker stop "${legacy[@]}" > /dev/null 2>&1 || true
    docker rm "${legacy[@]}" > /dev/null 2>&1 || true

    # 5. Limpar redes Docker órfãs relacionadas ao Vortex
    # NOTA: Não removemos vortex-simple, vortex-kafka-network e vortex-rabbitmq-network
    # pois elas são necessárias para inicializações subsequentes
    docker network rm vortex_default 2>/dev/null || true

    # 6. Verificar se ainda há containers do Vortex rodando (todos se chamam vortex-...)
    local remaining_ids
    remaining_ids=$(docker ps --filter "name=vortex" -q 2>/dev/null || true)
    if [[ -n "$remaining_ids" ]]; then
        print_color $YELLOW "⚠️  Ainda há $(wc -w <<< "$remaining_ids") container(s) rodando:"
        docker ps --filter "name=vortex" --format "table {{.Names}}\t{{.Status}}"
        print_color $YELLOW "💡 Forçando parada..."
        # Os mesmos IDs no stop e no rm (só os que estavam rodando; volumes não são tocados)
        # shellcheck disable=SC2086
        docker stop $remaining_ids > /dev/null 2>&1 || true
        # shellcheck disable=SC2086
        docker rm $remaining_ids > /dev/null 2>&1 || true
    fi

    print_color $GREEN "✅ Todos os serviços foram parados."
}

# Função para corrigir problemas do Kafka
fix_kafka_issues() {
    print_color $BLUE "🔧 Executando correção automática do Kafka..."

    if [[ -f "infra/kafka/fix-kafka-issues.sh" ]]; then
        chmod +x infra/kafka/fix-kafka-issues.sh
        if ./infra/kafka/fix-kafka-issues.sh; then
            print_color $GREEN "✅ Correção do Kafka concluída com sucesso!"
            print_color $GREEN "📡 Kafka Broker: localhost:9092"
            print_color $GREEN "🌐 Kafka UI (opcional): docker compose -f infra/docker/docker-compose.kafka-simple.yml --profile ui up -d kafka-ui"
        else
            print_color $RED "❌ Falha na correção do Kafka"
            exit 1
        fi
    else
        print_color $RED "❌ Script fix-kafka-issues.sh não encontrado!"
        print_color $YELLOW "💡 Certifique-se de que o arquivo está em infra/kafka/"
        exit 1
    fi
}

# Função para corrigir problemas de redes Docker
fix_docker_networks() {
    print_color $BLUE "🔧 Executando correção automática das redes Docker..."

    # Parar todos os containers relacionados primeiro
    print_color $YELLOW "🛑 Parando containers que podem estar usando as redes..."
    docker ps --filter "network=vortex-simple" -q | xargs -r docker stop 2>/dev/null || true
    docker ps --filter "network=vortex-kafka-network" -q | xargs -r docker stop 2>/dev/null || true
    docker ps --filter "network=vortex-rabbitmq-network" -q | xargs -r docker stop 2>/dev/null || true

    # Remover redes problemáticas
    print_color $BLUE "🧹 Removendo redes Docker problemáticas..."
    docker network rm vortex-simple 2>/dev/null || true
    docker network rm vortex-kafka-network 2>/dev/null || true
    docker network rm vortex-rabbitmq-network 2>/dev/null || true

    # Limpar redes órfãs
    print_color $BLUE "🧹 Limpando redes órfãs..."
    docker network prune -f

    # Aguardar um pouco
    sleep 3

    # Recriar redes necessárias
    print_color $BLUE "🌐 Recriando redes Docker necessárias..."

    # Lista de redes necessárias com suas configurações específicas
    local networks=("vortex-simple" "vortex-kafka-network" "vortex-rabbitmq-network")

    for network in "${networks[@]}"; do
        print_color $YELLOW "📡 Criando rede Docker: $network"
        # Criar rede com configurações específicas para evitar conflitos IPv4/IPv6
        docker network create \
            --driver bridge \
            --opt com.docker.network.enable_ipv6=false \
            --opt com.docker.network.bridge.enable_ip_masquerade=true \
            "$network" >/dev/null 2>&1 || true
        print_color $GREEN "✅ Rede Docker $network criada"
    done

    print_color $GREEN "✅ Correção das redes Docker concluída com sucesso!"
    print_color $GREEN "💡 Agora você pode executar novamente: ./start-vortex.sh"
}

# Função para limpar ambiente
clean_environment() {
    print_color $YELLOW "🧹 Limpando ambiente..."
    print_color $RED "⚠️  ATENÇÃO: Todos os dados do banco Oracle, do Kafka e do RabbitMQ serão perdidos!"
    print_color $YELLOW "   (o volume docker_auth-db-data, do PostgreSQL do serviço de autorização, é mantido)"
    read -p "Tem certeza? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        stop_services
        # Só aqui, com confirmação, os volumes das stacks são apagados (down -v)
        prd_compose down -v 2>/dev/null || true
        docker compose -f infra/docker/docker-compose.full.yml down -v 2>/dev/null || true
        docker compose -f infra/docker/docker-compose.full-kafka.yml --profile '*' down -v 2>/dev/null || true
        docker compose -f infra/docker/docker-compose.full-rabbitmq.yml down -v 2>/dev/null || true
        docker compose -f infra/docker/docker-compose.kafka.yml --profile '*' down -v 2>/dev/null || true
        docker compose -f infra/docker/docker-compose.kafka-simple.yml --profile '*' down -v 2>/dev/null || true
        docker compose -f infra/docker/docker-compose.rabbitmq.yml --profile '*' down -v 2>/dev/null || true
        docker compose -f "$LEVE_COMPOSE" down 2>/dev/null || true

        # Remover redes do Vortex durante limpeza completa
        docker network rm vortex-simple 2>/dev/null || true
        docker network rm vortex-kafka-network 2>/dev/null || true
        docker network rm vortex-rabbitmq-network 2>/dev/null || true

        # Sem "docker system prune": ele apagaria containers parados e caches de outros projetos

        # Recriar redes necessárias após limpeza
        print_color $BLUE "🌐 Recriando redes Docker necessárias após limpeza..."
        ensure_docker_networks

        print_color $GREEN "✅ Ambiente limpo e redes recriadas."
    else
        print_color $YELLOW "❌ Operação cancelada."
    fi
}

# ================================================================
# MENSAGERIA E BANCOS
# ================================================================

# Função para aguardar Kafka estar pronto
wait_for_kafka() {
    local max_attempts=60
    local attempt=1

    print_color $BLUE "⏳ Aguardando Kafka estar completamente pronto..."

    while [[ $attempt -le $max_attempts ]]; do
        # Container saudável (healthcheck do compose: listener aberto)...
        if docker ps --filter "name=vortex-kafka" --filter "health=healthy" --format "{{.Names}}" | grep -q "vortex-kafka"; then
            # ...e o listener do host respondendo. A imagem nativa não traz as CLIs do Kafka.
            if (exec 3<>/dev/tcp/127.0.0.1/9092) 2>/dev/null; then
                print_color $GREEN "✅ Kafka broker está respondendo em localhost:9092!"
                return 0
            fi
        fi

        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando Kafka ficar pronto"
            return 1
        fi

        print_color $YELLOW "   ⏳ Aguardando Kafka... ($attempt/$max_attempts)"
        sleep 2
        attempt=$((attempt + 1))
    done
}

# Função para iniciar Kafka
start_kafka() {
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        # Se vai usar o compose completo com Kafka, não iniciar separadamente
        if [[ "$ENVIRONMENT" == "prd" && "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
            print_color $BLUE "🚀 Kafka será iniciado junto com a stack completa..."
            return 0
        fi

        print_color $BLUE "🚀 Iniciando Apache Kafka (broker nativo, KRaft, sem ZooKeeper)..."

        if [[ -f "infra/docker/docker-compose.kafka-simple.yml" ]]; then
            print_color $GREEN "📦 Usando infra/docker/docker-compose.kafka-simple.yml..."

            # Limpar containers antigos se existirem (down sem -v: nenhum volume é apagado)
            print_color $YELLOW "🧹 Limpando containers Kafka antigos..."
            compose_down infra/docker/docker-compose.kafka-simple.yml
            compose_down infra/docker/docker-compose.kafka.yml

            # A porta 9092 precisa estar livre (só processo do host é encerrado; container não)
            if ! ensure_port_free 9092 "Kafka"; then
                return 1
            fi

            # Iniciar só o broker (a Kafka UI fica no profile ui)
            if ! safe_docker_compose "infra/docker/docker-compose.kafka-simple.yml" "up -d" "kafka"; then
                print_color $RED "❌ Falha ao iniciar Kafka via docker compose!"
                return 1
            fi

            # Usar função centralizada para aguardar Kafka
            if ! wait_for_kafka; then
                print_color $YELLOW "💡 Executando correção automática..."

                # Executar script de correção se disponível
                if [[ -f "infra/kafka/fix-kafka-issues.sh" ]]; then
                    chmod +x infra/kafka/fix-kafka-issues.sh
                    ./infra/kafka/fix-kafka-issues.sh
                    return $?
                else
                    return 1
                fi
            fi

            # Tópicos: o backend cria os seus (KafkaConfig) e o auto-create está ligado
            print_color $GREEN "📋 Tópicos: criados pelo próprio backend (KafkaConfig) e por auto-create no broker"

        elif [[ -f "infra/docker/docker-compose.kafka.yml" ]]; then
            print_color $YELLOW "📦 Usando infra/docker/docker-compose.kafka.yml..."
            if ! safe_docker_compose "infra/docker/docker-compose.kafka.yml" "up -d" "kafka"; then
                print_color $RED "❌ Falha ao iniciar Kafka via docker compose!"
                return 1
            fi
            wait_for_kafka || return 1
        else
            print_color $RED "❌ Nenhum arquivo de configuração Kafka encontrado!"
            print_color $YELLOW "💡 Arquivos esperados: infra/docker/docker-compose.kafka-simple.yml ou infra/docker/docker-compose.kafka.yml"
            return 1
        fi

        # Verificar se Kafka está rodando
        if docker ps --format "{{.Names}}" | grep -q "vortex-kafka"; then
            print_color $GREEN "✅ Kafka iniciado com sucesso"
            print_color $GREEN "   📡 Kafka Broker: localhost:9092 (containers: kafka:19092)"
            print_color $GREEN "   🌐 Kafka UI (opcional): docker compose -f infra/docker/docker-compose.kafka-simple.yml --profile ui up -d kafka-ui"
        else
            print_color $RED "❌ Falha ao iniciar Kafka"
            print_color $YELLOW "💡 Tente executar: ./infra/kafka/fix-kafka-issues.sh"
            return 1
        fi
    fi
}

# Função para iniciar RabbitMQ
start_rabbitmq() {
    if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
        # Se vai usar o compose completo com RabbitMQ, não iniciar separadamente
        if [[ "$ENVIRONMENT" == "prd" && "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
            print_color $BLUE "🐰 RabbitMQ será iniciado junto com a stack completa..."
            return 0
        fi

        print_color $BLUE "🐰 Iniciando RabbitMQ..."

        # Verificar se arquivo RabbitMQ existe
        if [[ -f "infra/docker/docker-compose.rabbitmq.yml" ]]; then
            print_color $GREEN "📦 Usando configuração RabbitMQ..."

            # Limpar containers antigos se existirem (down sem -v: as filas persistidas ficam)
            print_color $YELLOW "🧹 Limpando containers RabbitMQ antigos..."
            compose_down infra/docker/docker-compose.rabbitmq.yml

            # Verificar se portas estão livres
            ensure_port_free 5672 "RabbitMQ AMQP" || return 1
            ensure_port_free 15672 "RabbitMQ Management" || return 1

            # Iniciar RabbitMQ
            if ! safe_docker_compose "infra/docker/docker-compose.rabbitmq.yml" "up -d" "rabbitmq"; then
                print_color $RED "❌ Falha ao iniciar RabbitMQ via docker compose!"
                return 1
            fi

            # Aguardar RabbitMQ estar pronto
            if ! wait_for_rabbitmq; then
                print_color $RED "❌ Falha ao iniciar RabbitMQ"
                return 1
            fi

        else
            print_color $RED "❌ Arquivo de configuração RabbitMQ não encontrado!"
            print_color $YELLOW "💡 Arquivo esperado: infra/docker/docker-compose.rabbitmq.yml"
            return 1
        fi

        # Verificar se RabbitMQ está rodando
        if docker ps --format "{{.Names}}" | grep -q "vortex-rabbitmq"; then
            print_color $GREEN "✅ RabbitMQ iniciado com sucesso"
            print_color $GREEN "   🌐 Management UI: http://localhost:15672"
            print_color $GREEN "   📡 AMQP Port: localhost:5672"
            print_color $GREEN "   👤 Usuário: vortex / Senha: vortex123"
        else
            print_color $RED "❌ Falha ao iniciar RabbitMQ"
            return 1
        fi
    fi
}

# Função para aguardar RabbitMQ estar pronto
wait_for_rabbitmq() {
    print_color $BLUE "⏳ Aguardando RabbitMQ estar pronto..."

    local max_attempts=30
    local attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        if docker exec vortex-rabbitmq rabbitmq-diagnostics status >/dev/null 2>&1; then
            print_color $GREEN "✅ RabbitMQ está pronto!"
            return 0
        fi

        print_color $YELLOW "⏳ Tentativa $attempt/$max_attempts - Aguardando RabbitMQ..."
        sleep 3
        attempt=$((attempt + 1))
    done

    print_color $RED "❌ Timeout aguardando RabbitMQ"
    return 1
}

# Função para aguardar Oracle estar pronto
wait_for_oracle() {
    local max_attempts=60
    local attempt=1

    print_color $BLUE "⏳ Aguardando Oracle Database estar pronto..."

    while [[ $attempt -le $max_attempts ]]; do
        if docker logs vortex-db 2>&1 | grep -q "DATABASE IS READY TO USE"; then
            print_color $GREEN "✅ Oracle Database está pronto!"
            return 0
        fi

        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando Oracle Database"
            return 1
        fi

        print_color $YELLOW "   ⏳ Aguardando Oracle Database... ($attempt/$max_attempts)"
        sleep 5
        attempt=$((attempt + 1))
    done
}

# Função para aguardar PostgreSQL estar pronto
wait_for_postgresql() {
    local max_attempts=30
    local attempt=1

    print_color $BLUE "⏳ Aguardando PostgreSQL estar pronto..."

    while [[ $attempt -le $max_attempts ]]; do
        if docker exec vortex-auth-db pg_isready -U vortex_auth -d vortex_auth >/dev/null 2>&1; then
            print_color $GREEN "✅ PostgreSQL está pronto!"
            return 0
        fi

        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando PostgreSQL"
            return 1
        fi

        print_color $YELLOW "   ⏳ Aguardando PostgreSQL... ($attempt/$max_attempts)"
        sleep 2
        attempt=$((attempt + 1))
    done
}

# Função para iniciar bancos de dados
start_databases() {
    print_color $BLUE "🗄️  Iniciando bancos de dados..."

    local databases_started=false

    # SEMPRE iniciar PostgreSQL para serviço de autorização (tanto dev quanto prd)
    print_color $BLUE "🐘 Iniciando PostgreSQL para serviço de autorização..."

    # Verificar se o container já existe
    if docker ps -a --format "{{.Names}}" | grep -q "^vortex-auth-db$"; then
        if ! docker ps --format "{{.Names}}" | grep -q "^vortex-auth-db$"; then
            print_color $YELLOW "🔄 PostgreSQL já existe mas está parado. Iniciando..."
            docker start vortex-auth-db > /dev/null
        else
            print_color $GREEN "✅ PostgreSQL já está rodando"
        fi
    else
        # Verificar se existe docker compose para auth e criar o container
        if [[ -f "infra/docker/docker-compose.auth.yml" ]]; then
            print_color $BLUE "📦 Criando e iniciando PostgreSQL via docker compose..."
            if ! safe_docker_compose "infra/docker/docker-compose.auth.yml" "up -d" "auth-db"; then
                print_color $RED "❌ Falha ao iniciar PostgreSQL via docker compose!"
                return 1
            fi
        else
            print_color $RED "❌ Arquivo docker-compose.auth.yml não encontrado!"
            return 1
        fi
    fi

    # Aguardar PostgreSQL estar pronto
    if docker ps --format "{{.Names}}" | grep -q "^vortex-auth-db$"; then
        wait_for_postgresql || return 1
        databases_started=true

        # Verificar configuração de porta
        print_color $BLUE "🔗 Verificando configuração de porta..."
        local port_mapping
        port_mapping=$(docker port vortex-auth-db 2>/dev/null | grep "5432/tcp" | head -1 | cut -d':' -f2)
        if [[ -n "$port_mapping" ]]; then
            print_color $GREEN "✅ PostgreSQL mapeado para porta $port_mapping"
        else
            print_color $YELLOW "⚠️  Aviso: Mapeamento de porta não encontrado"
        fi
    else
        print_color $RED "❌ Falha ao iniciar PostgreSQL"
        return 1
    fi

    # Iniciar Oracle para ambiente de produção (na stack completa ele sobe junto com ela)
    if [[ "$ENVIRONMENT" == "prd" && ! ( "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ) ]]; then
        print_color $BLUE "🏛️  Iniciando Oracle Database Free..."

        # Verificar se o container já existe
        if docker ps -a --format "{{.Names}}" | grep -q "^vortex-db$"; then
            if ! docker ps --format "{{.Names}}" | grep -q "^vortex-db$"; then
                print_color $YELLOW "🔄 Oracle já existe mas está parado. Iniciando..."
                docker start vortex-db > /dev/null
            else
                print_color $GREEN "✅ Oracle já está rodando"
            fi
        else
            # Iniciar Oracle via docker compose (serviço "db" do docker-compose.yml)
            print_color $BLUE "   🐳 docker compose up -d db (a primeira vez baixa a imagem do Oracle Free)"
            prd_compose up -d db > /dev/null 2>&1 || true
        fi

        # Aguardar Oracle estar pronto
        if docker ps --format "{{.Names}}" | grep -q "^vortex-db$"; then
            wait_for_oracle || return 1
            databases_started=true
        else
            print_color $RED "❌ Falha ao iniciar Oracle (veja: docker compose -f infra/docker/docker-compose.yml up db)"
            return 1
        fi
    fi

    if [[ "$databases_started" == "true" ]]; then
        print_color $GREEN "✅ Bancos de dados iniciados com sucesso!"

        # Mostrar informações dos bancos
        if [[ "$ENVIRONMENT" == "prd" ]]; then
            if docker ps --format "{{.Names}}" | grep -q "^vortex-db$"; then
                print_color $GREEN "   🏛️  Oracle: localhost:1521/FREEPDB1 (vortex_user)"
            fi
        fi

        if docker ps --format "{{.Names}}" | grep -q "^vortex-auth-db$"; then
            print_color $GREEN "   🐘 PostgreSQL: localhost:5432 (vortex_auth/vortex_auth_password)"
        fi
    else
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            print_color $GREEN "✅ Ambiente de desenvolvimento usa H2 (em memória)"
        else
            print_color $YELLOW "⚠️  Nenhum banco de dados foi iniciado"
        fi
    fi
}

# Função para aguardar o healthcheck do backend
wait_for_backend_healthcheck() {
    local host=$1
    local port=$2
    local url="http://$host:$port/health"

    print_color $BLUE "⏳ Aguardando healthcheck do backend em $url..."
    if wait_for_http "$url" "${3:-180}" "${4:-}"; then
        print_color $GREEN "✅ Backend está saudável!"
        return 0
    fi
    print_color $RED "❌ Timeout aguardando healthcheck do backend em $url"
    return 1
}

# Função para aguardar o healthcheck do serviço de autorização
wait_for_auth_healthcheck() {
    local host=$1
    local port=$2
    local url="http://$host:$port/q/health"

    print_color $BLUE "⏳ Aguardando healthcheck do serviço de autorização em $url..."
    if wait_for_http "$url" "${3:-180}" "${4:-}"; then
        print_color $GREEN "✅ Serviço de autorização está saudável!"
        return 0
    fi
    print_color $RED "❌ Timeout aguardando healthcheck do serviço de autorização em $url"
    return 1
}

# ================================================================
# PERFIS E VARIÁVEIS POR PROCESSO (nada é exportado para o launcher inteiro)
# ================================================================

# Perfis Spring: base (dev|prd) + leve (só modo leve no dev) + mensageria
app_spring_profiles() {
    local profiles="$1"
    if [[ "$RUN_MODE" == "leve" && "$1" == "dev" ]]; then
        profiles+=",leve"
    fi
    case "$MESSAGING_SYSTEM" in
        kafka|rabbitmq|sqs) profiles+=",$MESSAGING_SYSTEM" ;;
    esac
    echo "$profiles"
}

# Variáveis de mensageria do backend principal (lidas pelos application-*.properties)
APP_MSG_ENV=()
app_messaging_env() {
    case "$MESSAGING_SYSTEM" in
        kafka)
            APP_MSG_ENV=(KAFKA_ENABLED=true KAFKA_BOOTSTRAP_SERVERS=localhost:9092 RABBITMQ_ENABLED=false)
            ;;
        rabbitmq)
            APP_MSG_ENV=(RABBITMQ_ENABLED=true RABBITMQ_HOST=localhost RABBITMQ_PORT=5672
                RABBITMQ_USERNAME=vortex RABBITMQ_PASSWORD=vortex123 RABBITMQ_VHOST=vortex-vhost
                KAFKA_ENABLED=false)
            ;;
        *)
            APP_MSG_ENV=(KAFKA_ENABLED=false RABBITMQ_ENABLED=false)
            ;;
    esac
}

# Perfil Quarkus: "leve" no modo leve; no modo dev o quarkus:dev já usa "dev". O perfil de
# mensageria entra na lista (o Quarkus aceita vários, separados por vírgula).
auth_quarkus_profile() {
    local base
    if [[ "$RUN_MODE" == "leve" ]]; then base="leve"; else base="dev"; fi
    case "$MESSAGING_SYSTEM" in
        kafka|rabbitmq) echo "$base,$MESSAGING_SYSTEM" ;;
        *) echo "$base" ;;
    esac
}

AUTH_MSG_ENV=()
auth_messaging_env() {
    case "$MESSAGING_SYSTEM" in
        kafka)
            AUTH_MSG_ENV=(KAFKA_BOOTSTRAP_SERVERS=localhost:9092)
            ;;
        rabbitmq)
            AUTH_MSG_ENV=(RABBITMQ_HOST=localhost RABBITMQ_PORT=5672 RABBITMQ_USERNAME=vortex
                RABBITMQ_PASSWORD=vortex123 RABBITMQ_VHOST=vortex-vhost)
            ;;
        *)
            AUTH_MSG_ENV=()
            ;;
    esac
}

# ================================================================
# BUILD SÓ DO QUE MUDOU (modo leve e frontends de prd)
# ================================================================

# Hash do conteúdo (e dos nomes) dos arquivos indicados, relativo à pasta do módulo.
# Aceita globs (ex.: 'tsconfig*.json'), expandidos dentro da pasta.
tree_hash() {
    local dir=$1
    shift
    (
        cd "$dir" || exit 1
        shopt -s nullglob dotglob
        local items=() pattern match
        for pattern in "$@"; do
            # shellcheck disable=SC2206
            for match in $pattern; do
                [[ -e "$match" ]] && items+=("$match")
            done
        done
        if [[ ${#items[@]} -eq 0 ]]; then
            echo "vazio"
            exit 0
        fi
        find "${items[@]}" -type f ! -name '.vortex-build' -print0 \
            | LC_ALL=C sort -z | xargs -0 -r sha256sum | sha256sum | cut -d' ' -f1
    )
}

app_boot_jar() {
    local jar
    for jar in "$APP_BACKEND_DIR"/target/*.jar; do
        if [[ -f "$jar" ]]; then
            echo "$jar"
            return 0
        fi
    done
    return 1
}

# Build de um backend com ./mvnw só se o carimbo (src/, pom.xml, .mvn/ e versão do Java)
# mudou ou o artefato sumiu. $1 nome, $2 rótulo, $3 pasta, $4 artefato (glob, relativo à pasta)
build_backend_if_needed() {
    local name=$1 label=$2 dir=$3 artifact=$4
    local stamp_file="$dir/target/.vortex-build" stamp artifact_ok=false
    stamp=$( { tree_hash "$dir" src pom.xml .mvn; java_version_line; } | sha256sum | cut -d' ' -f1 )
    if compgen -G "$dir/$artifact" > /dev/null; then
        artifact_ok=true
    fi
    if [[ "$artifact_ok" == "true" && -f "$stamp_file" && "$(cat "$stamp_file")" == "$stamp" ]]; then
        print_color $GREEN "✅ $label: sem mudanças desde o último build (build pulado)"
        return 0
    fi
    print_color $BLUE "🔨 Build de $label (./mvnw -B -q clean package -DskipTests)..."
    local t0=$SECONDS log="logs/build-$name.log"
    if ! ( cd "$dir" && exec ./mvnw -B -q clean package -DskipTests < /dev/null ) > "$log" 2>&1; then
        print_color $RED "❌ Falha no build de $label. Últimas linhas de $log:"
        tail -30 "$log"
        return 1
    fi
    echo "$stamp" > "$stamp_file"
    print_color $GREEN "✅ $label compilado em $((SECONDS - t0))s"
}

# npm ci só se node_modules não existe ou o package-lock.json mudou desde a última instalação
ensure_node_modules() {
    local name=$1 label=$2 dir=$3
    local lock="$dir/package-lock.json" marker="$dir/node_modules/.vortex-lock" lock_hash
    if [[ ! -f "$lock" ]]; then
        print_color $RED "❌ $lock não encontrado (o launcher usa npm ci)."
        return 1
    fi
    lock_hash=$(sha256sum "$lock" | cut -d' ' -f1)
    if [[ -d "$dir/node_modules" ]]; then
        if [[ -f "$marker" && "$(cat "$marker")" == "$lock_hash" ]]; then
            return 0
        fi
        # Primeira vez com este launcher: adota o node_modules instalado depois da última
        # mudança do lock (o npm grava node_modules/.package-lock.json a cada instalação)
        if [[ ! -f "$marker" && -f "$dir/node_modules/.package-lock.json" && ! "$lock" -nt "$dir/node_modules/.package-lock.json" ]]; then
            echo "$lock_hash" > "$marker"
            return 0
        fi
    fi
    print_color $BLUE "📦 npm ci em $label (node_modules ausente ou package-lock.json mudou)..."
    local t0=$SECONDS log="logs/npm-ci-$name.log"
    if ! ( cd "$dir" && exec npm ci --no-audit --no-fund < /dev/null ) > "$log" 2>&1; then
        print_color $RED "❌ npm ci falhou em $label. Últimas linhas de $log:"
        tail -30 "$log"
        return 1
    fi
    echo "$lock_hash" > "$marker"
    print_color $GREEN "✅ Dependências de $label instaladas em $((SECONDS - t0))s"
}

# Build de um frontend só se o carimbo mudou (código, configs, .env*, tema, VITE_* e Node).
# $1 nome, $2 rótulo, $3 pasta, demais: VAR=valor de build (VITE_*)
build_frontend_if_needed() {
    local name=$1 label=$2 dir=$3
    shift 3
    local vite_env=("$@")
    ensure_node_modules "$name" "$label" "$dir" || return 1
    local stamp stamp_file="$dir/dist/.vortex-build"
    stamp=$( {
        tree_hash "$dir" src public index.html package.json package-lock.json 'vite.config.*' 'tsconfig*.json' env.d.ts '.env*'
        echo "tema=$COLOR_THEME"
        printf '%s\n' "${vite_env[@]}"
        node --version
    } | sha256sum | cut -d' ' -f1 )
    if [[ -f "$dir/dist/index.html" && -f "$stamp_file" && "$(cat "$stamp_file")" == "$stamp" ]]; then
        print_color $GREEN "✅ $label: sem mudanças desde o último build (dist/ reaproveitado)"
        return 0
    fi
    print_color $BLUE "🔨 Build de $label (npm run build)..."
    local t0=$SECONDS log="logs/build-$name.log"
    if ! ( cd "$dir" && exec env "${vite_env[@]}" npm run build < /dev/null ) > "$log" 2>&1; then
        print_color $RED "❌ Falha no build de $label. Últimas linhas de $log:"
        tail -30 "$log"
        return 1
    fi
    echo "$stamp" > "$stamp_file"
    print_color $GREEN "✅ $label compilado em $((SECONDS - t0))s"
}

build_app_frontend_if_needed() {
    build_frontend_if_needed "frontend" "Frontend principal" "$APP_FRONTEND_DIR" \
        "VITE_API_BASE_URL=$FRONT_VITE_API_BASE_URL" \
        "VITE_AUTH_BACKEND_URL=$FRONT_VITE_AUTH_BACKEND_URL" \
        "VITE_AUTH_SERVICE_URL=$FRONT_VITE_AUTH_SERVICE_URL"
}

build_auth_frontend_if_needed() {
    build_frontend_if_needed "auth-frontend" "Frontend de autorização" "$AUTH_FRONTEND_DIR" \
        "VITE_API_URL=$FRONT_VITE_API_URL"
}

# Modo leve: todos os builds antes de subir qualquer JVM, um de cada vez (pico de RAM menor)
build_leve_artifacts() {
    print_color $BLUE "🏗️  Modo leve: compilando só o que mudou, um build por vez..."
    generate_theme_config
    build_backend_if_needed "auth-backend" "Backend de autorização" "$AUTH_BACKEND_DIR" "target/quarkus-app/quarkus-run.jar" || return 1
    if [[ "$RUN_BACKEND" == "true" && "$ENVIRONMENT" == "dev" ]]; then
        build_backend_if_needed "backend" "Backend principal" "$APP_BACKEND_DIR" "target/*.jar" || return 1
    fi
    build_auth_frontend_if_needed || return 1
    if [[ "$RUN_FRONTEND" == "true" ]]; then
        build_app_frontend_if_needed || return 1
    fi
}

# ================================================================
# MODO LEVE: JVMs com cache AOT e nginx estático
# ================================================================

# O cache precisa ser refeito se não existe, se foi treinado com outras flags/perfis/JDK/build,
# ou se algum arquivo do layout ficou mais novo que ele. $1 cache, $2 pasta do layout, $3 carimbo
aot_cache_stale() {
    local cache=$1 layout_dir=$2 stamp=$3 cache_name
    cache_name=$(basename "$cache")
    [[ -f "$cache" && -f "$cache.stamp" ]] || return 0
    [[ "$(cat "$cache.stamp")" == "$stamp" ]] || return 0
    if [[ -n "$(find "$layout_dir" -type f -newer "$cache" ! -name "${cache_name}*" -print -quit 2>/dev/null)" ]]; then
        return 0
    fi
    return 1
}

# Sobe uma JVM do modo leve (com o cache AOT se existir) e espera o health.
# Na primeira subida com um cache novo usa -XX:AOTMode=on, que exige o cache: se a JVM subir,
# o cache está valendo de verdade. Depois disso, modo padrão (auto): cache velho nunca trava.
# $1 serviço (backend|auth-backend), $2 rótulo, $3 pasta, $4 cache (relativo à pasta),
# $5 URL do health, $6 flags da JVM. LAUNCH_ENV e LAUNCH_ARGS vêm do chamador.
LAUNCH_ENV=()
LAUNCH_ARGS=()
launch_leve_jvm() {
    local svc=$1 label=$2 dir=$3 aot_rel=$4 health_url=$5 jvm_flags=$6
    local log="logs/$svc.log" pidfile="logs/$svc.pid"
    local aot_flags=() validating=false cache_state="não"
    if [[ -f "$dir/$aot_rel" ]]; then
        aot_flags=("-XX:AOTCache=$aot_rel")
        cache_state="sim"
        if [[ ! -f "$dir/$aot_rel.validated" ]]; then
            aot_flags+=("-XX:AOTMode=on" "-Xlog:aot=info")
            validating=true
        fi
    fi

    # shellcheck disable=SC2086
    ( cd "$dir" && exec env "${LAUNCH_ENV[@]}" nohup java $jvm_flags "${aot_flags[@]}" "${LAUNCH_ARGS[@]}" < /dev/null ) > "$log" 2>&1 &
    local pid=$!
    echo "$pid" > "$pidfile"
    print_color $GREEN "✅ $label iniciado (PID: $pid, log: $log)"

    local rc=0
    wait_for_http "$health_url" 180 "$pid" || rc=$?
    if [[ $rc -eq 0 ]]; then
        if [[ "$validating" == "true" ]]; then
            echo "validado com -XX:AOTMode=on em $(date '+%Y-%m-%d %H:%M:%S')" > "$dir/$aot_rel.validated"
            print_color $GREEN "   ✅ Cache AOT validado: a JVM subiu com -XX:AOTMode=on ($(grep -m1 -o 'Opened AOT cache.*' "$log" || echo 'cache aberto'))"
        fi
        record_startup_time "$svc" "$cache_state" "$WAIT_ELAPSED_MS" "$log"
        return 0
    fi

    # Com AOTMode=on a JVM recusa subir se o cache não servir: o cache recusado é apagado (a
    # próxima subida treina de novo) e a JVM sobe agora sem ele
    if [[ "$validating" == "true" ]] && ! kill -0 "$pid" 2>/dev/null; then
        print_color $YELLOW "⚠️  A JVM não aceitou o cache AOT com -XX:AOTMode=on (log em ${log%.log}-aot-falhou.log)."
        print_color $YELLOW "   Cache apagado (a próxima subida treina de novo); subindo agora sem ele..."
        mv -f "$log" "${log%.log}-aot-falhou.log"
        rm -f "$dir/$aot_rel" "$dir/$aot_rel".*
        launch_leve_jvm "$@"
        return $?
    fi

    if [[ $rc -eq 2 ]]; then
        print_color $RED "❌ $label terminou durante a subida. Últimas linhas de $log:"
    else
        print_color $RED "❌ $label não respondeu em $health_url. Últimas linhas de $log:"
    fi
    tail -30 "$log"
    return 1
}

# Treino do cache AOT do backend de autorização: sobe, espera o /q/health 200 e manda TERM.
# O cache é gravado na saída: a JVM do treino dispara uma segunda JVM que monta o cache, então
# os dois treinos nunca rodam ao mesmo tempo.
train_auth_aot() {
    local dir=$1 aot_rel=$2 profile=$3 stamp=$4
    print_color $BLUE "🧠 Treinando o cache AOT do backend de autorização (uma vez: sobe, espera o health e sai)..."
    rm -f "$dir/$aot_rel" "$dir/$aot_rel".*
    local log="logs/aot-train-auth.log"
    # shellcheck disable=SC2086
    ( cd "$dir" && exec env "${AUTH_MSG_ENV[@]}" java $AUTH_JVM_FLAGS -XX:AOTCacheOutput="$aot_rel" -Dquarkus.profile="$profile" -jar target/quarkus-app/quarkus-run.jar < /dev/null ) > "$log" 2>&1 &
    local pid=$!
    echo "$pid" > logs/aot-train-auth.pid
    local rc=0
    wait_for_http "http://localhost:8081/q/health" 180 "$pid" || rc=$?
    if [[ $rc -ne 0 ]]; then
        print_color $RED "❌ O treino do auth não ficou saudável. Últimas linhas de $log:"
        tail -20 "$log"
        stop_pid_file logs/aot-train-auth.pid "Treino do cache AOT do auth" "java"
        return 1
    fi
    record_startup_time "auth-backend" "não (treino)" "$WAIT_ELAPSED_MS" "$log"
    local t0
    t0=$(now_ms)
    print_color $BLUE "   ⏳ TERM enviado; a JVM grava o cache AOT na saída..."
    kill -TERM "$pid" 2>/dev/null || true
    if ! wait_pid_exit "$pid" 180; then
        print_color $YELLOW "⚠️  O treino não terminou em 180 s: KILL (sem cache desta vez)"
        kill -KILL "$pid" 2>/dev/null || true
    fi
    wait "$pid" 2>/dev/null || true
    rm -f logs/aot-train-auth.pid
    if [[ ! -f "$dir/$aot_rel" ]]; then
        print_color $YELLOW "⚠️  O cache AOT do auth não foi gerado (veja $log); seguindo sem ele."
        return 1
    fi
    echo "$stamp" > "$dir/$aot_rel.stamp"
    print_color $GREEN "✅ Cache AOT do auth criado: $dir/$aot_rel ($(du -h "$dir/$aot_rel" | cut -f1), montagem em $(fmt_secs $(( $(now_ms) - t0 )))s)"
}

# Treino do cache AOT do backend principal: o contexto Spring sobe e a JVM sai logo depois
# do refresh (-Dspring.context.exit=onRefresh); o cache é montado na saída.
train_app_aot() {
    local dir=$1 aot_rel=$2 jar_rel=$3 profiles=$4 stamp=$5
    print_color $BLUE "🧠 Treinando o cache AOT do backend principal (uma vez: sobe o contexto e sai)..."
    rm -f "$dir/$aot_rel" "$dir/$aot_rel".*
    local log="logs/aot-train-backend.log" t0
    t0=$(now_ms)
    # shellcheck disable=SC2086
    ( cd "$dir" && exec env "${APP_MSG_ENV[@]}" java $APP_JVM_FLAGS -XX:AOTCacheOutput="$aot_rel" -Dspring.context.exit=onRefresh -jar "$jar_rel" --spring.profiles.active="$profiles" < /dev/null ) > "$log" 2>&1 &
    local pid=$!
    echo "$pid" > logs/aot-train-backend.pid
    if ! wait_pid_exit "$pid" 300; then
        print_color $YELLOW "⚠️  O treino do backend não terminou em 300 s: KILL (sem cache desta vez)"
        kill -KILL "$pid" 2>/dev/null || true
    fi
    local rc=0
    wait "$pid" 2>/dev/null || rc=$?
    rm -f logs/aot-train-backend.pid
    if [[ ! -f "$dir/$aot_rel" ]]; then
        print_color $YELLOW "⚠️  O cache AOT do backend não foi gerado (saída $rc, veja $log); seguindo sem ele."
        return 1
    fi
    echo "$stamp" > "$dir/$aot_rel.stamp"
    print_color $GREEN "✅ Cache AOT do backend criado: $dir/$aot_rel ($(du -h "$dir/$aot_rel" | cut -f1), treino + montagem em $(fmt_secs $(( $(now_ms) - t0 )))s)"
}

# Backend de autorização no modo leve: java -jar do quarkus-app com o perfil leve e cache AOT
start_auth_backend_leve() {
    print_color $BLUE "🔐 Iniciando Backend de Autorização (modo leve: jar + cache AOT)..."
    local dir="$AUTH_BACKEND_DIR" run_jar="target/quarkus-app/quarkus-run.jar" aot_rel="target/quarkus-app/app.aot"
    if [[ ! -f "$dir/$run_jar" ]]; then
        print_color $RED "❌ $dir/$run_jar não existe (o build falhou?)."
        return 1
    fi
    ensure_port_free 8081 "Auth Backend" || return 1

    local profile stamp build_id
    profile=$(auth_quarkus_profile)
    auth_messaging_env
    build_id=$(cat "$dir/target/.vortex-build" 2>/dev/null || sha256sum "$dir/$run_jar" | cut -d' ' -f1)
    stamp="flags=$AUTH_JVM_FLAGS|perfil=$profile|java=$(java_version_line)|build=$build_id"

    if aot_cache_stale "$dir/$aot_rel" "$dir/target/quarkus-app" "$stamp"; then
        train_auth_aot "$dir" "$aot_rel" "$profile" "$stamp" || true
    else
        print_color $GREEN "✅ Cache AOT do auth em dia ($dir/$aot_rel)"
    fi

    LAUNCH_ENV=("${AUTH_MSG_ENV[@]}")
    LAUNCH_ARGS=("-Dquarkus.profile=$profile" -jar "$run_jar")
    launch_leve_jvm "auth-backend" "Backend de autorização (perfil $profile)" "$dir" "$aot_rel" \
        "http://localhost:8081/q/health" "$AUTH_JVM_FLAGS"
}

# Backend principal no modo leve (ambiente dev): jar extraído (o cache AOT exige o layout
# extraído e os mesmos timestamps de jar) + perfis dev,leve[,mensageria] + cache AOT
start_backend_leve() {
    print_color $BLUE "🔧 Iniciando Backend principal (modo leve: jar extraído + cache AOT)..."
    local dir="$APP_BACKEND_DIR" boot_jar jar_name
    if ! boot_jar=$(app_boot_jar); then
        print_color $RED "❌ Nenhum jar em $dir/target (o build falhou?)."
        return 1
    fi
    jar_name=$(basename "$boot_jar")
    local leve_rel="target/leve" jar_rel="target/leve/$jar_name" aot_rel="target/leve/app.aot"
    ensure_port_free 8080 "Main Backend" || return 1

    # 1) Extração, refeita só quando o jar muda
    local jar_sum
    jar_sum=$(sha256sum "$boot_jar" | cut -d' ' -f1)
    if [[ ! -f "$dir/$jar_rel" || ! -f "$dir/$leve_rel/.extracted" || "$(cat "$dir/$leve_rel/.extracted")" != "$jar_sum" ]]; then
        print_color $BLUE "📦 Extraindo $jar_name em $dir/$leve_rel (java -Djarmode=tools ... extract)..."
        rm -rf "${dir:?}/${leve_rel:?}"
        if ! ( cd "$dir" && exec java -Djarmode=tools -jar "target/$jar_name" extract --destination "$leve_rel" < /dev/null ) > logs/extract-backend.log 2>&1; then
            print_color $RED "❌ Falha ao extrair o jar (veja logs/extract-backend.log)."
            return 1
        fi
        echo "$jar_sum" > "$dir/$leve_rel/.extracted"
    fi

    # 2) Cache AOT, treinado com as mesmas flags e os mesmos perfis da execução
    local profiles stamp
    profiles=$(app_spring_profiles dev)
    app_messaging_env
    stamp="flags=$APP_JVM_FLAGS|perfis=$profiles|java=$(java_version_line)|jar=$jar_sum"
    if aot_cache_stale "$dir/$aot_rel" "$dir/$leve_rel" "$stamp"; then
        train_app_aot "$dir" "$aot_rel" "$jar_rel" "$profiles" "$stamp" || true
    else
        print_color $GREEN "✅ Cache AOT do backend em dia ($dir/$aot_rel)"
    fi

    # 3) Execução, sempre a partir do target/leve/<jar>
    LAUNCH_ENV=("${APP_MSG_ENV[@]}")
    LAUNCH_ARGS=(-jar "$jar_rel" "--spring.profiles.active=$profiles")
    launch_leve_jvm "backend" "Backend principal (perfis $profiles)" "$dir" "$aot_rel" \
        "http://localhost:8080/health" "$APP_JVM_FLAGS"
}

# nginx estático (vortex-static) com os dois dist/ nas portas 5173 e 3001
start_static_server() {
    print_color $BLUE "🌐 Subindo o nginx estático (vortex-static) com os dois frontends..."
    # Bind mount de pasta inexistente faria o Docker criá-la como root e quebrar o próximo build
    mkdir -p "$APP_FRONTEND_DIR/dist" "$AUTH_FRONTEND_DIR/dist"
    ensure_port_free 5173 "Main Frontend" || return 1
    ensure_port_free 3001 "Auth Frontend" || return 1
    if ! safe_docker_compose "$LEVE_COMPOSE" "up -d" "vortex-static"; then
        print_color $RED "❌ Falha ao subir o vortex-static"
        return 1
    fi
    if wait_for_http "http://localhost:3001/" 30 && { [[ "$RUN_FRONTEND" != "true" ]] || wait_for_http "http://localhost:5173/" 30; }; then
        print_color $GREEN "✅ nginx estático pronto: http://localhost:5173 e http://localhost:3001"
        return 0
    fi
    print_color $RED "❌ O vortex-static não respondeu (docker logs vortex-static)"
    return 1
}

# ================================================================
# MODO DEV (hot reload) E AMBIENTE PRD
# ================================================================

# Grava o PID da JVM filha (a que escuta na porta) para o --stop pará-la direto
record_child_jvm_pid() {
    local port=$1 parent_pid=$2 pidfile=$3 child
    child=$(lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -1 || true)
    if [[ -n "$child" && "$child" != "$parent_pid" ]]; then
        echo "$child" > "$pidfile"
    fi
}

# Função para executar backend de autorização em desenvolvimento (quarkus:dev)
start_auth_backend_dev() {
    print_color $BLUE "🔐 Iniciando Backend de Autorização em modo desenvolvimento (quarkus:dev)..."

    # Verificar se há processos na porta 8081 (conforme PORTS.md)
    ensure_port_free 8081 "Auth Backend" || return 1

    # Perfil (dev, mais o de mensageria se houver) e teto de memória da JVM filha via jvm.args
    local profile jvm_args="$AUTH_DEV_JVM_FLAGS"
    profile=$(auth_quarkus_profile)
    if [[ "$profile" != "dev" ]]; then
        jvm_args+=" -Dquarkus.profile=$profile"
    fi
    auth_messaging_env

    if [[ "$MESSAGING_SYSTEM" == "kafka" ]] && ! docker ps --format "{{.Names}}" | grep -q "vortex-kafka"; then
        print_color $YELLOW "⚠️  Kafka não está rodando. Serviço de autorização pode ter problemas de conectividade."
    fi

    print_color $BLUE "🚀 Iniciando serviço de autorização (./mvnw quarkus:dev, JVM com $AUTH_DEV_JVM_FLAGS)..."
    ( cd "$AUTH_BACKEND_DIR" && exec env "${AUTH_MSG_ENV[@]}" MAVEN_OPTS="$DEV_MAVEN_OPTS" nohup ./mvnw -B quarkus:dev -Dquarkus.http.port=8081 "-Djvm.args=$jvm_args" < /dev/null ) > logs/auth-backend.log 2>&1 &
    local pid=$!
    echo "$pid" > logs/auth-backend.pid
    print_color $GREEN "✅ Backend de autorização iniciado (PID Maven: $pid)"

    # Aguardar healthcheck
    if wait_for_auth_healthcheck "localhost" "8081" 300 "$pid"; then
        record_child_jvm_pid 8081 "$pid" logs/auth-backend-jvm.pid
        record_startup_time "auth-backend" "não (quarkus:dev)" "$WAIT_ELAPSED_MS" logs/auth-backend.log
        return 0
    fi
    tail -30 logs/auth-backend.log
    return 1
}

# Servidor Vite (dev ou preview) com porta fixa e --strictPort: nunca edita o vite.config.ts.
# $1 nome, $2 rótulo, $3 pasta, $4 porta, demais: argumentos do vite
start_vite_server() {
    local name=$1 label=$2 dir=$3 port=$4
    shift 4
    ensure_port_free "$port" "$label" || return 1
    ( cd "$dir" && exec nohup ./node_modules/.bin/vite "$@" --port "$port" --strictPort < /dev/null ) > "logs/$name.log" 2>&1 &
    local pid=$!
    echo "$pid" > "logs/$name.pid"
    print_color $GREEN "✅ $label iniciado (PID: $pid, porta $port)"
    if wait_for_http "http://localhost:$port/" 60 "$pid"; then
        return 0
    fi
    print_color $RED "❌ $label não respondeu na porta $port. Últimas linhas de logs/$name.log:"
    tail -20 "logs/$name.log"
    return 1
}

# Função para executar frontend de autorização (modo dev; no modo leve quem serve é o vortex-static)
start_auth_frontend() {
    print_color $BLUE "🔐 Iniciando Frontend de Autorização..."

    if [[ "$ENVIRONMENT" == "dev" ]]; then
        ensure_node_modules "auth-frontend" "Frontend de autorização" "$AUTH_FRONTEND_DIR" || return 1
        print_color $GREEN "🔥 Iniciando servidor de desenvolvimento Vite na porta 3001..."
        start_vite_server "auth-frontend" "Frontend de autorização" "$AUTH_FRONTEND_DIR" 3001
    else
        print_color $GREEN "🏗️  Fazendo build do frontend de autorização para produção..."
        build_auth_frontend_if_needed || return 1
        print_color $GREEN "📦 Servindo frontend de autorização com preview na porta 3001..."
        start_vite_server "auth-frontend" "Frontend de autorização (preview)" "$AUTH_FRONTEND_DIR" 3001 preview
    fi
}

# Função para executar backend em desenvolvimento (spring-boot:run)
start_backend_dev() {
    print_color $BLUE "🔧 Iniciando Backend em modo desenvolvimento (spring-boot:run)..."

    # Definir perfis Spring baseado no sistema de mensageria
    local spring_profiles
    spring_profiles=$(app_spring_profiles dev)
    app_messaging_env

    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        # Verificar se Kafka está rodando antes de iniciar o backend
        if ! docker ps --format "{{.Names}}" | grep -q "vortex-kafka"; then
            print_color $YELLOW "⚠️  Kafka não está rodando. Tentando iniciar..."
            if ! start_kafka; then
                print_color $RED "❌ Falha ao iniciar Kafka. Backend pode ter problemas de conectividade."
            fi
        else
            # Kafka está rodando, mas vamos aguardar estar completamente pronto
            wait_for_kafka || true
        fi
    fi

    ensure_port_free 8080 "Main Backend" || return 1

    print_color $GREEN "📦 Executando com ./mvnw (JVM filha com $APP_DEV_JVM_FLAGS)..."
    ( cd "$APP_BACKEND_DIR" && exec env SPRING_PROFILES_ACTIVE="$spring_profiles" "${APP_MSG_ENV[@]}" MAVEN_OPTS="$DEV_MAVEN_OPTS" nohup ./mvnw -B spring-boot:run "-Dspring-boot.run.jvmArguments=$APP_DEV_JVM_FLAGS" < /dev/null ) > logs/backend.log 2>&1 &
    local pid=$!
    echo "$pid" > logs/backend.pid
    print_color $GREEN "✅ Backend iniciado (PID Maven: $pid) com perfis: $spring_profiles"

    # Aguardar healthcheck do backend
    if wait_for_backend_healthcheck "localhost" "8080" 300 "$pid"; then
        record_child_jvm_pid 8080 "$pid" logs/backend-jvm.pid
        record_startup_time "backend" "não (spring-boot:run)" "$WAIT_ELAPSED_MS" logs/backend.log
        return 0
    fi
    tail -30 logs/backend.log
    return 1
}

# Função para executar backend em produção (Docker + Oracle)
start_backend_prd() {
    print_color $BLUE "🚀 Iniciando Backend em modo produção..."

    # Definir perfis Spring baseado no sistema de mensageria
    local spring_profiles kafka_enabled=false
    spring_profiles=$(app_spring_profiles prd)

    # Garantir que o Kafka está rodando antes de iniciar o backend
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        kafka_enabled=true
        if [[ ! ( "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ) ]]; then
            if ! docker ps --format "{{.Names}}" | grep -q "vortex-kafka"; then
                print_color $YELLOW "⚠️  Kafka não está rodando. Iniciando Kafka primeiro..."
                if ! start_kafka; then
                    print_color $RED "❌ Falha ao iniciar Kafka. Backend não pode ser iniciado."
                    return 1
                fi
            elif ! wait_for_kafka; then
                print_color $YELLOW "⚠️  Kafka não está completamente pronto, mas continuando..."
            fi
        fi
    fi

    if [[ "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
        # Se frontend também deve rodar e npm não está disponível, usar compose completo
        print_color $YELLOW "📦 Usando Docker Compose completo (Backend + Oracle + Frontend)..."

        # Escolher arquivo de compose baseado no sistema de mensageria
        local compose_file="infra/docker/docker-compose.full.yml"
        if [[ "$MESSAGING_SYSTEM" == "kafka" && -f "infra/docker/docker-compose.full-kafka.yml" ]]; then
            compose_file="infra/docker/docker-compose.full-kafka.yml"
            print_color $BLUE "🚀 Usando stack completa com Kafka integrado..."
        elif [[ "$MESSAGING_SYSTEM" == "rabbitmq" && -f "infra/docker/docker-compose.full-rabbitmq.yml" ]]; then
            compose_file="infra/docker/docker-compose.full-rabbitmq.yml"
            print_color $BLUE "🐰 Usando stack completa com RabbitMQ integrado..."
        fi

        # Variáveis só para este docker compose (subshell: nada vaza para o launcher)
        if ! ( export SPRING_PROFILES_ACTIVE="$spring_profiles" KAFKA_ENABLED="$kafka_enabled"
               safe_docker_compose "$compose_file" "up -d --build" ); then
            print_color $RED "❌ Falha ao iniciar stack completa via docker compose!"
            return 1
        fi

        print_color $GREEN "✅ Stack completa iniciada com perfis: $spring_profiles"
    else
        # Apenas backend + Oracle (docker-compose.yml + override)
        print_color $BLUE "   🐳 docker compose up -d --build (o build do backend roda dentro do Docker)"
        if ! ( export SPRING_PROFILES_ACTIVE="$spring_profiles" KAFKA_ENABLED="$kafka_enabled"
               prd_compose up -d --build ); then
            print_color $RED "❌ Falha ao iniciar backend + Oracle via docker compose!"
            return 1
        fi
        print_color $GREEN "✅ Backend e Oracle iniciados com perfis: $spring_profiles"
    fi

    # Aguardar healthcheck do backend no Docker
    wait_for_backend_healthcheck "localhost" "8080" 600
}

# Função para gerar arquivo de configuração do tema.
# Só reescreve o .env.local quando o conteúdo muda: reescrever sempre faria todo build rodar.
generate_theme_config() {
    local main_frontend_dir="$APP_FRONTEND_DIR"
    local auth_frontend_dir="$AUTH_FRONTEND_DIR"
    local content file changed=false

    print_color $BLUE "🎨 Configurando tema ${COLOR_THEME}..."

    content=$(printf '%s\n' \
        "# Arquivo gerado automaticamente pelo start-vortex.sh" \
        "# Configuração do tema de cores selecionado pelo usuário" \
        "" \
        "VITE_THEME_COLOR=${COLOR_THEME}")

    for file in "${main_frontend_dir}/.env.local" "${auth_frontend_dir}/.env.local"; do
        if [[ -f "$file" && "$(cat "$file")" == "$content" ]]; then
            continue
        fi
        printf '%s\n' "$content" > "$file"
        changed=true
    done

    if [[ "$changed" == "true" ]]; then
        print_color $GREEN "✅ Tema ${COLOR_THEME} configurado em ambos frontends:"
        print_color $GREEN "   - ${main_frontend_dir}/.env.local"
        print_color $GREEN "   - ${auth_frontend_dir}/.env.local"
    else
        print_color $GREEN "✅ Tema ${COLOR_THEME} já estava configurado (.env.local sem mudança)"
    fi
}

# Função para executar frontend (modo dev; no modo leve quem serve é o vortex-static)
start_frontend() {
    print_color $BLUE "🎨 Iniciando Frontend..."

    # Se já foi iniciado via Docker Compose completo, pular
    if [[ "$ENVIRONMENT" == "prd" && "$NPM_AVAILABLE" == "false" ]]; then
        print_color $GREEN "✅ Frontend já iniciado via Docker Compose"
        return 0
    fi

    if [[ "$NPM_AVAILABLE" != "true" ]]; then
        print_color $RED "❌ npm não disponível e node_modules não existe."
        print_color $YELLOW "💡 Frontend será executado via Docker na próxima execução"
        return 1
    fi

    if [[ "$ENVIRONMENT" == "dev" ]]; then
        ensure_node_modules "frontend" "Frontend principal" "$APP_FRONTEND_DIR" || return 1
        print_color $GREEN "🔥 Iniciando servidor de desenvolvimento Vite na porta 5173..."
        if [[ "${VITE_DEVTOOLS:-}" == "true" ]]; then
            print_color $YELLOW "   🧩 VITE_DEVTOOLS=true: Vue DevTools ligado (gasta mais memória)"
        fi
        start_vite_server "frontend" "Frontend dev server" "$APP_FRONTEND_DIR" 5173
    else
        print_color $GREEN "🏗️  Fazendo build do frontend para produção..."
        build_app_frontend_if_needed || return 1
        print_color $GREEN "📦 Servindo frontend com preview na porta 4173..."
        start_vite_server "frontend" "Frontend preview" "$APP_FRONTEND_DIR" 4173 preview
    fi
}

# ================================================================
# STATUS
# ================================================================

# Função para mostrar status
show_status() {
    print_color $CYAN "
📊 STATUS DOS SERVIÇOS (modo $RUN_MODE, ambiente $ENVIRONMENT)
═══════════════════════════════════════════════════════════════
"

    # Status do serviço de autorização
    print_color $BLUE "🔐 SERVIÇO DE AUTORIZAÇÃO:"

    # Backend de autorização
    if http_ok "http://localhost:8081/q/health"; then
        print_color $GREEN "   ✅ Backend de autorização rodando"
        print_color $GREEN "   🌐 API: http://localhost:8081"
        print_color $GREEN "   📚 Swagger: http://localhost:8081/q/swagger-ui"
        print_color $GREEN "   💚 Health: http://localhost:8081/q/health"
    else
        print_color $RED "   ❌ Backend de autorização não está rodando"
    fi

    # Frontend de autorização
    if [[ "$RUN_MODE" == "leve" ]]; then
        if http_ok "http://localhost:3001/"; then
            print_color $GREEN "   ✅ Frontend de autorização servido pelo nginx estático (vortex-static)"
            print_color $GREEN "   🌐 App: http://localhost:3001"
        else
            print_color $RED "   ❌ Frontend de autorização não responde em http://localhost:3001"
        fi
    elif [[ -f "logs/auth-frontend.pid" ]]; then
        PID=$(cat logs/auth-frontend.pid)
        if ps -p "$PID" > /dev/null 2>&1; then
            print_color $GREEN "   ✅ Frontend de autorização rodando (PID: $PID)"
            print_color $GREEN "   🌐 App: http://localhost:3001"
        else
            print_color $RED "   ❌ Frontend de autorização não está rodando"
        fi
    else
        print_color $RED "   ❌ Frontend de autorização não foi iniciado"
    fi

    # Status do sistema de mensageria
    if [[ "$MESSAGING_SYSTEM" != "none" ]]; then
        print_color $BLUE "📨 SISTEMA DE MENSAGERIA ($MESSAGING_SYSTEM):"

        if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
            local kafka_container
            kafka_container=$(docker ps --format "{{.Names}}" | grep -m1 -E "^vortex-kafka(-simple)?$" || true)
            if [[ -n "$kafka_container" ]]; then
                print_color $GREEN "   ✅ Kafka rodando ($kafka_container, nativo, KRaft)"
                print_color $GREEN "   📡 Broker: localhost:9092"
                if docker ps --format "{{.Names}}" | grep -q "^vortex-kafka-ui"; then
                    print_color $GREEN "   🌐 Kafka UI: http://localhost:8090"
                fi

                # Verificar saúde do Kafka
                if docker ps --filter "name=$kafka_container" --filter "health=healthy" --format "{{.Names}}" | grep -q "$kafka_container"; then
                    print_color $GREEN "   💚 Status: Saudável"
                else
                    print_color $YELLOW "   ⚠️  Status: Inicializando..."
                fi
            else
                print_color $RED "   ❌ Kafka não está rodando"
                print_color $YELLOW "   💡 Execute: ./start-vortex.sh --fix-kafka"
            fi
        fi

        if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
            if docker ps --format "{{.Names}}" | grep -q "vortex-rabbitmq"; then
                print_color $GREEN "   ✅ RabbitMQ rodando"
                print_color $GREEN "   📡 AMQP: localhost:5672"
                print_color $GREEN "   🌐 Management UI: http://localhost:15672"
                print_color $GREEN "   👤 Usuário: vortex / Senha: vortex123"

                # Verificar saúde do RabbitMQ
                if docker exec vortex-rabbitmq rabbitmq-diagnostics status >/dev/null 2>&1; then
                    print_color $GREEN "   💚 Status: Saudável"
                else
                    print_color $YELLOW "   ⚠️  Status: Inicializando..."
                fi
            else
                print_color $RED "   ❌ RabbitMQ não está rodando"
                print_color $YELLOW "   💡 Execute: ./start-vortex.sh -m rabbitmq"
            fi
        fi

        if [[ "$MESSAGING_SYSTEM" == "sqs" ]]; then
            print_color $YELLOW "   ☁️  SQS: Configuração AWS necessária"
        fi
    fi

    # Status do backend principal
    if [[ "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "🔧 BACKEND PRINCIPAL ($ENVIRONMENT):"
        if http_ok "http://localhost:8080/health"; then
            print_color $GREEN "   ✅ Rodando"
            print_color $GREEN "   🌐 API: http://localhost:8080"
            print_color $GREEN "   📚 Swagger: http://localhost:8080/swagger-ui.html"
            if [[ "$ENVIRONMENT" == "dev" ]]; then
                print_color $GREEN "   🗄️  H2 Console: http://localhost:8080/h2-console"
            else
                print_color $GREEN "   🗄️  Oracle: localhost:1521/FREEPDB1 (vortex_user)"
            fi
        else
            print_color $RED "   ❌ Não está rodando ou não responde ao healthcheck"
        fi
    fi

    # Status do frontend principal
    if [[ "$RUN_FRONTEND" == "true" ]]; then
        print_color $BLUE "🎨 FRONTEND PRINCIPAL ($ENVIRONMENT):"

        if [[ "$RUN_MODE" == "leve" ]]; then
            if http_ok "http://localhost:5173/"; then
                print_color $GREEN "   ✅ Servido pelo nginx estático (vortex-static)"
                print_color $GREEN "   🌐 App: http://localhost:5173"
            else
                print_color $RED "   ❌ Não responde em http://localhost:5173"
            fi
        # Verificar se está rodando via Docker
        elif docker ps --format "{{.Names}}" | grep -q "^vortex-frontend$"; then
            print_color $GREEN "   ✅ Rodando no Docker"
            print_color $GREEN "   🌐 App: http://localhost:3000"
        elif [[ -f "logs/frontend.pid" ]]; then
            PID=$(cat logs/frontend.pid)
            if ps -p "$PID" > /dev/null 2>&1; then
                print_color $GREEN "   ✅ Rodando (PID: $PID)"
                if [[ "$ENVIRONMENT" == "dev" ]]; then
                    print_color $GREEN "   🌐 App: http://localhost:5173"
                else
                    print_color $GREEN "   🌐 App: http://localhost:4173"
                fi
            else
                print_color $RED "   ❌ Não está rodando"
            fi
        else
            print_color $RED "   ❌ Não está rodando"
        fi
    fi

    print_color $CYAN "
📋 COMANDOS ÚTEIS:
═══════════════════════════════════════════════════════════════"
    print_color $YELLOW "   ./start-vortex.sh --stop          # Parar todos os serviços"
    print_color $YELLOW "   ./start-vortex.sh --clean         # Limpar ambiente"
    print_color $YELLOW "   ./scripts/medir-memoria.sh        # RSS por processo e container, RAM da VM e do Windows"
    print_color $YELLOW "   docker logs vortex-app -f         # Logs do backend (prd)"
    print_color $YELLOW "   docker logs vortex-db -f          # Logs do Oracle"
    print_color $YELLOW "   tail -f logs/backend.log          # Logs do backend (dev e leve)"
    print_color $YELLOW "   tail -f logs/frontend.log         # Logs do frontend (modo dev)"
    print_color $YELLOW "   tail -f logs/auth-backend.log     # Logs do backend de autorização"
    print_color $YELLOW "   tail -f logs/auth-frontend.log    # Logs do frontend de autorização (modo dev)"
    print_color $YELLOW "   docker logs vortex-static -f      # Logs do nginx estático (modo leve)"
    print_color $YELLOW "   cat logs/startup-times.log        # Tempos de subida das JVMs (com e sem cache AOT)"

    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        print_color $CYAN "
📨 COMANDOS KAFKA:
═══════════════════════════════════════════════════════════════"
        print_color $YELLOW "   docker logs vortex-kafka-simple -f                           # Logs do Kafka"
        print_color $YELLOW "   # A imagem nativa do broker não traz as CLIs; use a imagem JVM do Kafka:"
        print_color $YELLOW "   docker run --rm --network vortex-simple apache/kafka:4.4.0 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list"
        print_color $YELLOW "   docker compose -f infra/docker/docker-compose.kafka-simple.yml --profile ui up -d kafka-ui   # Kafka UI em :8090"
        print_color $YELLOW "   ./infra/kafka/fix-kafka-issues.sh                           # Correção automática de problemas"
    fi

    if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
        print_color $CYAN "
📨 COMANDOS RABBITMQ:
════════════════════════════════════════════════════════════════"
        print_color $YELLOW "   docker logs vortex-rabbitmq -f                              # Logs do RabbitMQ"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_queues         # Listar filas"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_exchanges      # Listar exchanges"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_bindings       # Listar bindings"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmq-diagnostics status     # Status do RabbitMQ"
    fi
}

# Tabela de memória (scripts/medir-memoria.sh)
show_memory_table() {
    if [[ -f "scripts/medir-memoria.sh" ]]; then
        bash scripts/medir-memoria.sh || true
    fi
}

# Resumo final: URLs, modo e memória
show_final_summary() {
    local front_url="http://localhost:5173"
    if [[ "$RUN_MODE" != "leve" && "$ENVIRONMENT" == "prd" ]]; then
        front_url="http://localhost:4173"
    fi
    local mode_desc="leve (jars + cache AOT, frontends no nginx estático)"
    if [[ "$RUN_MODE" == "dev" ]]; then
        mode_desc="dev (hot reload: Maven + Vite)"
    fi

    print_color $GREEN "
🎉 Vortex iniciado com sucesso!
═══════════════════════════════════════════════════════════════

⚙️  Modo: $mode_desc | Ambiente: $ENVIRONMENT | Mensageria: $MESSAGING_SYSTEM

🔐 SERVIÇOS DE AUTORIZAÇÃO:
   - Backend: http://localhost:8081
   - Frontend: http://localhost:3001
   - Swagger: http://localhost:8081/q/swagger-ui

🚀 SERVIÇOS PRINCIPAIS:
   - Backend: http://localhost:8080
   - Frontend: $front_url
"
    show_memory_table
    print_color $YELLOW "
⚠️  WSL: a distro desliga quando o último terminal dela fecha, e leva junto JVMs e containers.
   Deixe um terminal da distro aberto enquanto usar o Vortex.

Para parar os serviços, execute: ./start-vortex.sh --stop
"
}

# Função para mostrar logs
show_logs() {
    if [[ "$ENVIRONMENT" == "prd" && "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "📄 Mostrando logs do backend (produção)..."
        sleep 2
        docker logs vortex-app -f
    elif [[ "$ENVIRONMENT" == "dev" && "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "📄 Mostrando logs do backend (desenvolvimento)..."
        sleep 2
        if [[ -f "logs/backend.log" ]]; then
            tail -f logs/backend.log
        else
            echo "Logs não disponíveis"
        fi
    fi
}

# Falha de subida: avisa e sai (o que já subiu continua de pé até o --stop)
abort_start() {
    print_color $RED "❌ $1"
    print_color $YELLOW "💡 Para parar o que já subiu: ./start-vortex.sh --stop"
    exit 1
}

# Função principal
main() {
    cd "$SCRIPT_DIR"
    show_banner
    mkdir -p logs

    # Processar argumentos
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -e|--env)
                ENVIRONMENT="$2"
                shift 2
                ;;
            -r|--modo)
                RUN_MODE="$2"
                shift 2
                ;;
            -b|--backend-only)
                BACKEND_ONLY="true"
                shift
                ;;
            -f|--frontend-only)
                FRONTEND_ONLY="true"
                shift
                ;;
            -c|--components)
                COMPONENTS="$2"
                shift 2
                ;;
            -m|--messaging)
                MESSAGING_SYSTEM="$2"
                shift 2
                ;;
            --no-interaction)
                NO_INTERACTION="true"
                shift
                ;;
            --stop)
                stop_services
                exit 0
                ;;
            --clean)
                clean_environment
                exit 0
                ;;
            --fix-kafka)
                fix_kafka_issues
                exit 0
                ;;
            --fix-networks)
                fix_docker_networks
                exit 0
                ;;
            --logs)
                SHOW_LOGS="true"
                shift
                ;;
            --monitor-networks)
                print_color $BLUE "🔍 Iniciando monitoramento automático de redes Docker..."
                monitor_docker_networks &
                MONITOR_PID=$!
                print_color $GREEN "✅ Monitoramento de redes ativo (PID: $MONITOR_PID)"
                echo $MONITOR_PID > logs/network-monitor.pid
                exit 0
                ;;
            *)
                print_color $RED "❌ Opção desconhecida: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # Validar ambiente se fornecido
    if [[ -n "$ENVIRONMENT" && "$ENVIRONMENT" != "dev" && "$ENVIRONMENT" != "prd" ]]; then
        print_color $RED "❌ Ambiente inválido: $ENVIRONMENT. Use 'dev' ou 'prd'."
        exit 1
    fi

    # Validar modo se fornecido
    if [[ -n "$RUN_MODE" && "$RUN_MODE" != "leve" && "$RUN_MODE" != "dev" ]]; then
        print_color $RED "❌ Modo inválido: $RUN_MODE. Use 'leve' ou 'dev'."
        exit 1
    fi

    # Validar sistema de mensageria se fornecido
    if [[ -n "$MESSAGING_SYSTEM" && "$MESSAGING_SYSTEM" != "kafka" && "$MESSAGING_SYSTEM" != "rabbitmq" && "$MESSAGING_SYSTEM" != "sqs" && "$MESSAGING_SYSTEM" != "none" ]]; then
        print_color $RED "❌ Sistema de mensageria inválido: $MESSAGING_SYSTEM. Use 'kafka', 'rabbitmq', 'sqs' ou 'none'."
        exit 1
    fi

    # Validar componentes se fornecido
    if [[ -n "$COMPONENTS" && "$COMPONENTS" != "full" && "$COMPONENTS" != "backend" && "$COMPONENTS" != "frontend" ]]; then
        print_color $RED "❌ Componentes inválidos: $COMPONENTS. Use 'full', 'backend' ou 'frontend'."
        exit 1
    fi

    # Verificar pré-requisitos
    check_prerequisites

    # Java 27 e Node.js 26 (SDKMAN e nvm)
    load_toolchain

    # Garantir que as redes Docker necessárias existam
    ensure_docker_networks

    # Escolher cor do tema se não fornecido (PRIMEIRA pergunta)
    choose_color_theme

    # Escolher ambiente se não fornecido
    choose_environment

    # Escolher modo de execução (logo depois do ambiente)
    choose_mode

    # Escolher sistema de mensageria se não fornecido
    choose_messaging

    # Escolher componentes
    choose_components

    # Parar serviços existentes
    print_color $YELLOW "🔄 Verificando serviços existentes..."
    stop_services

    # Iniciar serviços
    print_color $BLUE "🚀 Iniciando serviços (modo $RUN_MODE, ambiente $ENVIRONMENT)..."
    local start_t0=$SECONDS

    # ZERO (modo leve): builds antes de qualquer JVM, um por vez e só do que mudou
    if [[ "$RUN_MODE" == "leve" ]]; then
        build_leve_artifacts || abort_start "Falha nos builds do modo leve"
    else
        generate_theme_config
    fi

    # PRIMEIRO: Iniciar bancos de dados (Oracle e PostgreSQL)
    start_databases || abort_start "Falha ao iniciar os bancos de dados"

    # SEGUNDO: Iniciar Kafka se necessário (apenas se não for integrado com stack completa)
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        if [[ "$ENVIRONMENT" == "prd" && "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
            print_color $BLUE "📦 Kafka será iniciado integrado com a stack completa..."
        else
            start_kafka || abort_start "Falha ao iniciar o Kafka"
        fi
    fi

    # TERCEIRO: Iniciar RabbitMQ se necessário (apenas se não for integrado com stack completa)
    if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
        if [[ "$ENVIRONMENT" == "prd" && "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
            print_color $BLUE "📦 RabbitMQ será iniciado integrado com a stack completa..."
        else
            start_rabbitmq || abort_start "Falha ao iniciar o RabbitMQ"
        fi
    fi

    # QUARTO: Iniciar serviços de autorização
    print_color $BLUE "🔐 Iniciando serviços de autorização..."
    if [[ "$RUN_MODE" == "leve" ]]; then
        start_auth_backend_leve || abort_start "Falha ao iniciar o backend de autorização"
    else
        start_auth_backend_dev || abort_start "Falha ao iniciar o backend de autorização"
        start_auth_frontend || abort_start "Falha ao iniciar o frontend de autorização"
    fi

    # QUINTO: backend principal
    if [[ "$RUN_BACKEND" == "true" ]]; then
        if [[ "$ENVIRONMENT" == "prd" ]]; then
            start_backend_prd || abort_start "Falha ao iniciar o backend principal (prd)"
        elif [[ "$RUN_MODE" == "leve" ]]; then
            start_backend_leve || abort_start "Falha ao iniciar o backend principal"
        else
            start_backend_dev || abort_start "Falha ao iniciar o backend principal"
        fi
    fi

    # SEXTO: frontends (modo leve: um nginx estático serve os dois)
    if [[ "$RUN_MODE" == "leve" ]]; then
        start_static_server || abort_start "Falha ao subir o nginx estático"
    elif [[ "$RUN_FRONTEND" == "true" ]]; then
        start_frontend || abort_start "Falha ao iniciar o frontend principal"
    fi

    print_color $GREEN "⏱️  Subida completa em $((SECONDS - start_t0))s"

    # Mostrar status
    show_status

    # Resumo final com URLs, modo e tabela de memória
    show_final_summary

    # Mostrar logs se solicitado
    if [[ "$SHOW_LOGS" == "true" ]]; then
        show_logs
    fi
}

# Variáveis globais
ENVIRONMENT=""
RUN_MODE=""
MESSAGING_SYSTEM=""
COMPONENTS=""
COLOR_THEME="${COLOR_THEME:-}"
BACKEND_ONLY="false"
FRONTEND_ONLY="false"
RUN_BACKEND="true"
RUN_FRONTEND="true"
SHOW_LOGS="false"
NO_INTERACTION="false"
NODE_AVAILABLE="false"
NPM_AVAILABLE="false"

# Executar função principal
main "$@"
