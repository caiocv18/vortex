#!/bin/bash

# ================================
# SCRIPT PARA RESOLVER PROBLEMAS DO KAFKA
# ================================
# Kafka nativo (apache/kafka-native) em KRaft de nó único, sem ZooKeeper
# (infra/docker/docker-compose.kafka-simple.yml). Os dados do broker ficam na camada do
# container, então recriar o container já resolve dado corrompido ou conflito de Cluster ID:
# nenhum volume é apagado e nada de "prune" global.

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_color() {
    echo -e "${1}${2}${NC}"
}

print_header() {
    echo
    print_color $BLUE "================================"
    print_color $BLUE "$1"
    print_color $BLUE "================================"
    echo
}

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/infra/docker"
COMPOSE_FILE="$DOCKER_DIR/docker-compose.kafka-simple.yml"

print_header "🔧 VORTEX - CORREÇÃO DE PROBLEMAS DO KAFKA"

# ================================
# 1. LIMPEZA DE CONTAINERS
# ================================

print_header "🧹 LIMPEZA DE CONTAINERS ANTIGOS"

print_color $YELLOW "Parando e removendo containers do Kafka (down sem -v)..."
docker compose -f "$COMPOSE_FILE" --profile '*' down 2>/dev/null || true
docker compose -f "$DOCKER_DIR/docker-compose.kafka.yml" --profile '*' down 2>/dev/null || true

# Containers da versão anterior (Confluent + ZooKeeper), se ainda existirem
docker stop vortex-zookeeper-simple vortex-zookeeper 2>/dev/null || true
docker rm vortex-zookeeper-simple vortex-zookeeper 2>/dev/null || true

print_color $YELLOW "Recriando a rede vortex-simple se faltar..."
docker network inspect vortex-simple > /dev/null 2>&1 || docker network create vortex-simple > /dev/null

print_color $GREEN "✅ Limpeza concluída!"

# ================================
# 2. VERIFICAÇÃO DE PORTAS
# ================================

print_header "🔍 VERIFICAÇÃO DE PORTAS"

check_port() {
    local port=$1
    local service=$2

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_color $RED "❌ Porta $port ($service) está em uso!"
        print_color $YELLOW "   Processo usando a porta:"
        lsof -Pi :$port -sTCP:LISTEN
        return 1
    else
        print_color $GREEN "✅ Porta $port ($service) está livre"
        return 0
    fi
}

PORTS_OK=true

check_port 9092 "Kafka" || PORTS_OK=false
check_port 8090 "Kafka UI" || PORTS_OK=false

if [ "$PORTS_OK" = false ]; then
    print_color $RED "❌ Algumas portas estão em uso. Por favor, pare os processos antes de continuar."
    exit 1
fi

# ================================
# 3. INICIALIZAÇÃO DO KAFKA SIMPLES
# ================================

print_header "🚀 INICIANDO KAFKA (KRaft, nativo)"

print_color $YELLOW "Iniciando Kafka com configuração simplificada..."
docker compose -f "$COMPOSE_FILE" up -d kafka

print_color $YELLOW "Aguardando inicialização do Kafka..."

max_attempts=60
attempt=1
kafka_healthy=false
recreated=false

while [[ $attempt -le $max_attempts ]]; do
    if docker ps --filter "name=vortex-kafka-simple" --filter "health=healthy" --format '{{.Names}}' | grep -q "vortex-kafka-simple"; then
        kafka_healthy=true
        print_color $GREEN "✅ Kafka inicializado com sucesso (tentativa $attempt/$max_attempts)"
        break
    fi

    # Conflito de Cluster ID ou storage inconsistente: recriar o container (uma vez)
    if [[ "$recreated" == "false" ]] && docker logs vortex-kafka-simple 2>&1 | grep -q "InconsistentClusterIdException\|Invalid cluster.id"; then
        print_color $YELLOW "⚠️  Detectado conflito de Cluster ID. Recriando o container (sem apagar volumes)..."
        docker compose -f "$COMPOSE_FILE" up -d --force-recreate kafka
        recreated=true
        attempt=1
        sleep 5
        continue
    fi

    if [[ $attempt -eq $max_attempts ]]; then
        print_color $RED "❌ Timeout aguardando Kafka inicializar após $max_attempts tentativas"
        break
    fi

    print_color $YELLOW "   ⏳ Aguardando... ($attempt/$max_attempts)"
    sleep 2
    attempt=$((attempt + 1))
done

if [[ "$kafka_healthy" != "true" ]]; then
    print_color $RED "❌ Falha na inicialização do Kafka"
    print_color $YELLOW "Mostrando logs para diagnóstico..."
    echo
    print_color $YELLOW "=== LOGS DO KAFKA ==="
    docker logs vortex-kafka-simple --tail 30
    exit 1
fi

# ================================
# 4. TESTE DE CONECTIVIDADE
# ================================

print_header "🔌 TESTE DE CONECTIVIDADE"

print_color $YELLOW "Testando o listener do host (localhost:9092)..."
if (exec 3<>/dev/tcp/127.0.0.1/9092) 2>/dev/null; then
    print_color $GREEN "✅ Kafka aceita conexões em localhost:9092"
else
    print_color $RED "❌ Kafka não aceita conexões em localhost:9092"
    exit 1
fi

if docker logs vortex-kafka-simple 2>&1 | grep -qi "Kafka Server started\|started (kafka.server"; then
    print_color $GREEN "✅ Broker registrou a subida no log"
fi

# ================================
# 5. CONFIGURAÇÃO PARA APLICAÇÃO
# ================================

print_header "⚙️ CONFIGURAÇÃO PARA APLICAÇÃO"

print_color $GREEN "📄 Configuração de teste pronta em: $PROJECT_ROOT/infra/kafka/application-kafka-test.properties"

# ================================
# 6. INSTRUÇÕES FINAIS
# ================================

print_header "📋 INSTRUÇÕES FINAIS"

print_color $GREEN "🎉 Kafka configurado com sucesso!"
echo
print_color $BLUE "🔗 Kafka Bootstrap Server: localhost:9092 (containers: kafka:19092)"
print_color $BLUE "📊 Kafka UI (opcional): docker compose -f $COMPOSE_FILE --profile ui up -d kafka-ui"
print_color $BLUE "📋 Tópicos (a imagem nativa não traz as CLIs):"
print_color $BLUE "   docker run --rm --network vortex-simple apache/kafka:4.4.0 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list"
echo
print_color $YELLOW "Para testar a aplicação:"
print_color $YELLOW "1. Execute: ./start-vortex.sh -e dev -m kafka (o backend sobe com o perfil kafka)"
print_color $YELLOW "2. Teste adicionando estoque em um produto"
echo
print_color $BLUE "Para parar o Kafka:"
print_color $BLUE "docker compose -f $COMPOSE_FILE down"
echo
print_color $GREEN "✅ Configuração concluída com sucesso!"
