#!/bin/bash

# Script para iniciar Apache Kafka
# Utiliza a configuração simplificada para melhor compatibilidade

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
KAFKA_DIR="$PROJECT_ROOT/infra/kafka"
DOCKER_DIR="$PROJECT_ROOT/infra/docker"

print_color $BLUE "🚀 Iniciando Apache Kafka..."
print_color $YELLOW "📍 Diretório do projeto: $PROJECT_ROOT"

# Verificar se arquivo kafka-simple existe
if [[ -f "$DOCKER_DIR/docker-compose.kafka-simple.yml" ]]; then
    print_color $GREEN "📦 Usando configuração Kafka simplificada..."
    
    # Limpar containers antigos se existirem
    print_color $YELLOW "🧹 Limpando containers Kafka antigos..."
    cd "$DOCKER_DIR"
    docker-compose -f docker-compose.kafka-simple.yml down -v 2>/dev/null || true
    docker-compose -f docker-compose.kafka.yml down -v 2>/dev/null || true
    
    # Verificar se portas estão livres
    if lsof -Pi :9092 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_color $YELLOW "⚠️  Porta 9092 ocupada. Tentando liberar..."
        pkill -f "kafka" 2>/dev/null || true
        sleep 2
    fi
    
    if lsof -Pi :2181 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_color $YELLOW "⚠️  Porta 2181 ocupada. Tentando liberar..."
        pkill -f "zookeeper" 2>/dev/null || true
        sleep 2
    fi
    
    # Iniciar Kafka
    docker-compose -f docker-compose.kafka-simple.yml up -d
    
    # Aguardar Kafka estar pronto
    print_color $BLUE "⏳ Aguardando Kafka estar completamente pronto..."
    local max_attempts=60
    local attempt=1
    
    while [[ $attempt -le $max_attempts ]]; do
        if docker ps --filter "name=nexdom-kafka-simple" --filter "health=healthy" | grep -q "nexdom-kafka-simple"; then
            print_color $GREEN "✅ Kafka está saudável!"
            
            if docker exec nexdom-kafka-simple kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; then
                print_color $GREEN "✅ Kafka broker está respondendo!"
                break
            fi
        fi
        
        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando Kafka ficar pronto"
            exit 1
        fi
        
        print_color $YELLOW "   ⏳ Aguardando Kafka... ($attempt/$max_attempts)"
        sleep 2
        ((attempt++))
    done
    
    # Criar tópicos necessários
    print_color $BLUE "📋 Criando tópicos necessários..."
    sleep 2
    
    local topics=("movimento-estoque" "produto-events" "alerta-estoque" "auditoria-events")
    for topic in "${topics[@]}"; do
        docker exec nexdom-kafka-simple kafka-topics --bootstrap-server localhost:9092 --create --topic "$topic" --partitions 3 --replication-factor 1 --if-not-exists 2>/dev/null || true
    done
    
    # Verificar tópicos criados
    print_color $GREEN "📊 Tópicos disponíveis:"
    docker exec nexdom-kafka-simple kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | sed 's/^/   ✓ /'
    
    print_color $GREEN "✅ Kafka iniciado com sucesso!"
    print_color $GREEN "   🌐 Kafka UI: http://localhost:8090"
    print_color $GREEN "   📡 Kafka Broker: localhost:9092"
    
else
    print_color $RED "❌ Arquivo docker-compose.kafka-simple.yml não encontrado!"
    print_color $YELLOW "💡 Esperado em: $DOCKER_DIR/docker-compose.kafka-simple.yml"
    exit 1
fi 