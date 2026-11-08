#!/bin/bash

# Script para iniciar Oracle Database
# Oracle Database Free 23ai (gvenzl/oracle-free) em container Docker

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

print_color $BLUE "🗄️  Iniciando Oracle Database Free..."
print_color $YELLOW "📍 Diretório do projeto: $PROJECT_ROOT"

# Verificar se arquivo docker-compose existe
if [[ -f "$DOCKER_DIR/docker-compose.yml" ]]; then
    print_color $GREEN "📦 Usando configuração Oracle..."

    cd "$DOCKER_DIR"

    # Verificar se container já está rodando
    if docker ps --format '{{.Names}}' | grep -q "^vortex-db$"; then
        print_color $YELLOW "⚠️  Oracle já está rodando!"
        print_color $GREEN "   🌐 Oracle: localhost:1521/FREEPDB1"
        exit 0
    fi

    # A rede vortex-simple é external no docker-compose.yml
    docker network inspect vortex-simple > /dev/null 2>&1 || docker network create vortex-simple > /dev/null

    # A rede vortex-rabbitmq-network também é external no docker-compose.yml (backend no -m rabbitmq)
    docker network inspect vortex-rabbitmq-network > /dev/null 2>&1 || docker network create vortex-rabbitmq-network > /dev/null

    # Iniciar Oracle (sem "down -v": o volume vortex-oracle-data guarda o banco entre subidas).
    # O docker-compose.yml exige JWT_SECRET (é do backend) e o Compose interpola o arquivo
    # inteiro: para subir só o banco, qualquer valor serve.
    print_color $BLUE "🚀 Iniciando Oracle Database..."
    JWT_SECRET="${JWT_SECRET:-nao-usado-pelo-oracle}" docker compose up -d db

    # Aguardar Oracle estar pronto
    print_color $BLUE "⏳ Aguardando Oracle estar completamente pronto..."
    print_color $YELLOW "   ⚠️  A primeira subida baixa a imagem e cria o volume; pode levar alguns minutos..."

    max_attempts=120  # 10 minutos
    attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        if docker ps --filter "name=vortex-db" --filter "health=healthy" --format '{{.Names}}' | grep -q "vortex-db"; then
            print_color $GREEN "✅ Oracle está saudável!"
            break
        fi

        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando Oracle ficar pronto"
            print_color $YELLOW "💡 Verifique os logs: docker logs vortex-db"
            exit 1
        fi

        print_color $YELLOW "   ⏳ Aguardando Oracle... ($attempt/$max_attempts)"
        sleep 5
        attempt=$((attempt + 1))
    done

    print_color $GREEN "✅ Oracle iniciado com sucesso!"
    print_color $GREEN "   🌐 Oracle: localhost:1521"
    print_color $GREEN "   📊 Serviço (PDB): FREEPDB1"
    print_color $GREEN "   👤 Usuário da aplicação: vortex_user / vortex_password"
    print_color $GREEN "   🔐 SYS e SYSTEM: senha de ORACLE_PASSWORD (padrão Oracle_1234)"
    print_color $GREEN "   🔗 JDBC: jdbc:oracle:thin:@//localhost:1521/FREEPDB1"

else
    print_color $RED "❌ Arquivo docker-compose.yml não encontrado!"
    print_color $YELLOW "💡 Esperado em: $DOCKER_DIR/docker-compose.yml"
    exit 1
fi
