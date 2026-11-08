#!/bin/bash

# Script para iniciar Apache Kafka
# Broker nativo (apache/kafka-native) em KRaft de nó único, sem ZooKeeper:
# infra/docker/docker-compose.kafka-simple.yml

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
COMPOSE_FILE="$DOCKER_DIR/docker-compose.kafka-simple.yml"

print_color $BLUE "🚀 Iniciando Apache Kafka (nativo, KRaft)..."
print_color $YELLOW "📍 Diretório do projeto: $PROJECT_ROOT"

if [[ ! -f "$COMPOSE_FILE" ]]; then
    print_color $RED "❌ Arquivo docker-compose.kafka-simple.yml não encontrado!"
    print_color $YELLOW "💡 Esperado em: $COMPOSE_FILE"
    exit 1
fi

print_color $GREEN "📦 Usando configuração Kafka simplificada..."

# A rede é external no compose (o start-vortex.sh também a cria)
docker network inspect vortex-simple > /dev/null 2>&1 || docker network create vortex-simple > /dev/null

# Limpar containers antigos se existirem (down sem -v: nenhum volume é apagado)
print_color $YELLOW "🧹 Limpando containers Kafka antigos..."
docker compose -f "$COMPOSE_FILE" --profile '*' down 2>/dev/null || true
docker compose -f "$DOCKER_DIR/docker-compose.kafka.yml" --profile '*' down 2>/dev/null || true

# Verificar se a porta está livre (quem estiver escutando recebe TERM; nada de pkill por nome)
if pids=$(lsof -t -iTCP:9092 -sTCP:LISTEN 2>/dev/null) && [[ -n "$pids" ]]; then
    print_color $YELLOW "⚠️  Porta 9092 ocupada (PID $(echo $pids)). Enviando TERM..."
    # shellcheck disable=SC2086
    kill -TERM $pids 2>/dev/null || true
    sleep 5
fi

# Iniciar Kafka (a Kafka UI fica no profile ui)
docker compose -f "$COMPOSE_FILE" up -d kafka

# Aguardar Kafka estar pronto
print_color $BLUE "⏳ Aguardando Kafka estar completamente pronto..."
max_attempts=60
attempt=1

while [[ $attempt -le $max_attempts ]]; do
    if docker ps --filter "name=vortex-kafka-simple" --filter "health=healthy" --format '{{.Names}}' | grep -q "vortex-kafka-simple"; then
        print_color $GREEN "✅ Kafka está saudável!"

        if (exec 3<>/dev/tcp/127.0.0.1/9092) 2>/dev/null; then
            print_color $GREEN "✅ Kafka broker está respondendo em localhost:9092!"
            break
        fi
    fi

    if [[ $attempt -eq $max_attempts ]]; then
        print_color $RED "❌ Timeout aguardando Kafka ficar pronto"
        exit 1
    fi

    print_color $YELLOW "   ⏳ Aguardando Kafka... ($attempt/$max_attempts)"
    sleep 2
    attempt=$((attempt + 1))
done

# Tópicos: o backend cria os seus (KafkaConfig) e o broker tem auto-create ligado.
# A imagem nativa não traz kafka-topics; para listar, use a imagem JVM do Kafka:
print_color $GREEN "📋 Para listar os tópicos:"
print_color $GREEN "   docker run --rm --network vortex-simple apache/kafka:4.4.0 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list"

print_color $GREEN "✅ Kafka iniciado com sucesso!"
print_color $GREEN "   📡 Kafka Broker: localhost:9092 (containers: kafka:19092)"
print_color $GREEN "   🌐 Kafka UI (opcional): docker compose -f $COMPOSE_FILE --profile ui up -d kafka-ui"
