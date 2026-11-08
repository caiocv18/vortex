#!/bin/bash

# Script para parar todos os serviços de infraestrutura
# Kafka, RabbitMQ, Oracle, nginx do modo leve e, no fim, qualquer container vortex-* que sobrar.
# Sempre "down" SEM -v: nenhum volume (dados) é apagado.

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

# Diretório base do projeto
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/infra/docker"

print_color $YELLOW "🛑 Parando todos os serviços de infraestrutura..."

cd "$DOCKER_DIR"

# Os compose de prd exigem JWT_SECRET, e o Compose interpola o arquivo inteiro até no "down".
# Para parar qualquer valor serve (nada sobe); sem ele o "down" falharia calado.
export JWT_SECRET="${JWT_SECRET:-nao-usado-no-down}"

down() {
    docker compose "$@" --profile '*' down 2>/dev/null || true
}

# Parar containers Docker em ordem específica para evitar problemas
# 1. Primeiro parar aplicações que dependem do Kafka
print_color $BLUE "🔄 Parando aplicações..."
down -f docker-compose.leve.yml
down -f docker-compose.full.yml
down -f docker-compose.full-kafka.yml
down -f docker-compose.full-rabbitmq.yml

# 2. Parar backend específico (docker-compose.yml + override)
print_color $BLUE "⚙️  Parando backend..."
docker compose down 2>/dev/null || true

# 3. Parar Kafka e RabbitMQ (todas as configurações possíveis)
print_color $BLUE "📨 Parando Kafka e RabbitMQ..."
down -f docker-compose.kafka-simple.yml
down -f docker-compose.kafka.yml
down -f docker-compose.rabbitmq.yml

# 4. Forçar parada de containers específicos se ainda estiverem rodando
print_color $BLUE "🔧 Forçando parada de containers específicos..."
docker stop vortex-kafka-simple vortex-kafka-ui-simple vortex-kafka vortex-kafka-ui vortex-zookeeper-simple vortex-zookeeper 2>/dev/null || true
docker stop vortex-app vortex-app-dev vortex-db vortex-frontend vortex-static 2>/dev/null || true

# 5. Remover containers órfãos
print_color $BLUE "🧹 Removendo containers órfãos..."
docker rm vortex-kafka-simple vortex-kafka-ui-simple vortex-kafka vortex-kafka-ui vortex-zookeeper-simple vortex-zookeeper 2>/dev/null || true
docker rm vortex-app vortex-app-dev vortex-db vortex-frontend vortex-static 2>/dev/null || true

# 6. Limpar redes Docker órfãs relacionadas ao Vortex (o start-vortex.sh recria as que precisa)
print_color $BLUE "🌐 Limpando redes..."
docker network rm vortex-kafka-network 2>/dev/null || true
docker network rm vortex_default 2>/dev/null || true
docker network rm vortex-simple 2>/dev/null || true

# 7. Aguardar um pouco para garantir que todos os containers foram parados
sleep 2

# 8. Verificar se ainda há containers do Vortex rodando
remaining_ids=$(docker ps --filter "name=vortex" -q)
if [[ -n "$remaining_ids" ]]; then
    print_color $YELLOW "⚠️  Ainda há $(wc -w <<< "$remaining_ids") container(s) rodando:"
    docker ps --filter "name=vortex" --format "table {{.Names}}\t{{.Status}}"
    print_color $YELLOW "💡 Forçando parada..."
    # shellcheck disable=SC2086
    docker stop $remaining_ids > /dev/null 2>&1 || true
    # shellcheck disable=SC2086
    docker rm $remaining_ids > /dev/null 2>&1 || true
fi

print_color $GREEN "✅ Todos os serviços de infraestrutura foram parados."
