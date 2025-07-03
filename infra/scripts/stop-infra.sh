#!/bin/bash

# Script para parar todos os serviços de infraestrutura
# Kafka, Oracle, e containers relacionados

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

# Parar containers Docker em ordem específica para evitar problemas
# 1. Primeiro parar aplicações que dependem do Kafka
print_color $BLUE "🔄 Parando aplicações..."
docker-compose -f docker-compose.full.yml down 2>/dev/null || true
docker-compose -f docker-compose.full-kafka.yml down 2>/dev/null || true

# 2. Parar backend específico
print_color $BLUE "⚙️  Parando backend..."
docker-compose down 2>/dev/null || true

# 3. Parar Kafka e Zookeeper (todas as configurações possíveis)
print_color $BLUE "📨 Parando Kafka..."
docker-compose -f docker-compose.kafka-simple.yml down 2>/dev/null || true
docker-compose -f docker-compose.kafka.yml down 2>/dev/null || true

# 4. Forçar parada de containers específicos se ainda estiverem rodando
print_color $BLUE "🔧 Forçando parada de containers específicos..."
docker stop nexdom-kafka-simple nexdom-zookeeper-simple nexdom-kafka-ui-simple 2>/dev/null || true
docker stop nexdom-kafka nexdom-zookeeper nexdom-kafka-ui 2>/dev/null || true
docker stop nexdom-app nexdom-app-dev nexdom-db nexdom-frontend 2>/dev/null || true

# 5. Remover containers órfãos
print_color $BLUE "🧹 Removendo containers órfãos..."
docker rm nexdom-kafka-simple nexdom-zookeeper-simple nexdom-kafka-ui-simple 2>/dev/null || true
docker rm nexdom-kafka nexdom-zookeeper nexdom-kafka-ui 2>/dev/null || true
docker rm nexdom-app nexdom-app-dev nexdom-db nexdom-frontend 2>/dev/null || true

# 6. Limpar redes Docker órfãs relacionadas ao Nexdom
print_color $BLUE "🌐 Limpando redes..."
docker network rm nexdom-kafka-network 2>/dev/null || true
docker network rm nexdom_default 2>/dev/null || true
docker network rm nexdom-simple 2>/dev/null || true

# 7. Aguardar um pouco para garantir que todos os containers foram parados
sleep 2

# 8. Verificar se ainda há containers do Nexdom rodando
local remaining_containers=$(docker ps --filter "name=nexdom" --format "{{.Names}}" | wc -l)
if [[ $remaining_containers -gt 0 ]]; then
    print_color $YELLOW "⚠️  Ainda há $remaining_containers container(s) rodando:"
    docker ps --filter "name=nexdom" --format "table {{.Names}}\t{{.Status}}"
    print_color $YELLOW "💡 Forçando parada..."
    docker ps --filter "name=nexdom" -q | xargs -r docker stop
    docker ps --filter "name=nexdom" -q | xargs -r docker rm
fi

print_color $GREEN "✅ Todos os serviços de infraestrutura foram parados." 