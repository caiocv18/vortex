#!/bin/bash

# Script para iniciar RabbitMQ
# Utiliza a configuração padrão do RabbitMQ para desenvolvimento

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Função para imprimir com cores
print_color() {
    echo -e "${1}${2}${NC}"
}

# Diretório base do projeto (assumindo que o script está em infra/scripts)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/infra/docker"

print_color $BLUE "🐰 Iniciando RabbitMQ..."
print_color $YELLOW "📍 Diretório do projeto: $PROJECT_ROOT"

# Verificar se arquivo RabbitMQ existe
if [[ -f "$DOCKER_DIR/docker-compose.rabbitmq.yml" ]]; then
    print_color $GREEN "📦 Usando configuração RabbitMQ..."

    cd "$DOCKER_DIR"

    # A rede é external no compose (o start-vortex.sh também a cria)
    docker network inspect vortex-rabbitmq-network > /dev/null 2>&1 || docker network create vortex-rabbitmq-network > /dev/null

    # Limpar containers antigos se existirem (down sem -v: as filas persistidas ficam)
    print_color $YELLOW "🧹 Limpando containers RabbitMQ antigos..."
    docker compose -f docker-compose.rabbitmq.yml --profile '*' down 2>/dev/null || true

    # Verificar se portas estão livres (quem estiver escutando recebe TERM; nada de pkill por nome)
    for port in 5672 15672; do
        if pids=$(lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null) && [[ -n "$pids" ]]; then
            print_color $YELLOW "⚠️  Porta $port ocupada (PID $(echo $pids)). Enviando TERM..."
            # shellcheck disable=SC2086
            kill -TERM $pids 2>/dev/null || true
            sleep 3
        fi
    done

    # Iniciar RabbitMQ (o container de ferramentas fica no profile tools)
    docker compose -f docker-compose.rabbitmq.yml up -d rabbitmq

    # Aguardar RabbitMQ estar pronto
    print_color $BLUE "⏳ Aguardando RabbitMQ estar pronto..."
    max_attempts=30
    attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        if docker exec vortex-rabbitmq rabbitmq-diagnostics status >/dev/null 2>&1; then
            print_color $GREEN "✅ RabbitMQ está pronto!"
            break
        fi

        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando RabbitMQ ficar pronto"
            exit 1
        fi

        print_color $YELLOW "⏳ Tentativa $attempt/$max_attempts - Aguardando RabbitMQ..."
        sleep 3
        attempt=$((attempt + 1))
    done

    # Verificar se RabbitMQ está rodando
    if docker ps --format '{{.Names}}' | grep -q "vortex-rabbitmq"; then
        print_color $GREEN "✅ RabbitMQ iniciado com sucesso!"
        print_color $GREEN "   🌐 Management UI: http://localhost:15672"
        print_color $GREEN "   📡 AMQP Port: localhost:5672"
        print_color $GREEN "   👤 Usuário: vortex / Senha: vortex123"
        print_color $GREEN "   🏠 Virtual Host: vortex-vhost"

        # Mostrar informações adicionais
        print_color $CYAN "
📋 COMANDOS ÚTEIS:
═══════════════════════════════════════════════════════════════"
        print_color $YELLOW "   docker logs vortex-rabbitmq -f                              # Logs do RabbitMQ"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_queues         # Listar filas"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_exchanges      # Listar exchanges"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_bindings       # Listar bindings"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmq-diagnostics status     # Status do RabbitMQ"

    else
        print_color $RED "❌ Falha ao iniciar RabbitMQ"
        exit 1
    fi

else
    print_color $RED "❌ Arquivo docker-compose.rabbitmq.yml não encontrado!"
    print_color $YELLOW "💡 Esperado em: $DOCKER_DIR/docker-compose.rabbitmq.yml"
    exit 1
fi
