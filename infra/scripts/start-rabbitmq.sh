#!/bin/bash

# Script para iniciar RabbitMQ
# Utiliza a configuração padrão do RabbitMQ para desenvolvimento

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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
    
    # Limpar containers antigos se existirem
    print_color $YELLOW "🧹 Limpando containers RabbitMQ antigos..."
    cd "$DOCKER_DIR"
    docker-compose -f docker-compose.rabbitmq.yml down -v 2>/dev/null || true
    
    # Verificar se portas estão livres
    if lsof -Pi :5672 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_color $YELLOW "⚠️  Porta 5672 ocupada. Tentando liberar..."
        pkill -f "rabbitmq" 2>/dev/null || true
        sleep 2
    fi
    
    if lsof -Pi :15672 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_color $YELLOW "⚠️  Porta 15672 ocupada. Tentando liberar..."
        pkill -f "rabbitmq" 2>/dev/null || true
        sleep 2
    fi
    
    # Iniciar RabbitMQ
    docker-compose -f docker-compose.rabbitmq.yml up -d
    
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
        ((attempt++))
    done
    
    # Verificar se RabbitMQ está rodando
    if docker ps | grep -q "vortex-rabbitmq"; then
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