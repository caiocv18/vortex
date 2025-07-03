#!/bin/bash

# Script para iniciar Oracle Database
# Gerencia a inicialização do Oracle em container Docker

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

print_color $BLUE "🗄️  Iniciando Oracle Database..."
print_color $YELLOW "📍 Diretório do projeto: $PROJECT_ROOT"

# Verificar se arquivo docker-compose existe
if [[ -f "$DOCKER_DIR/docker-compose.yml" ]]; then
    print_color $GREEN "📦 Usando configuração Oracle..."
    
    cd "$DOCKER_DIR"
    
    # Verificar se container já está rodando
    if docker ps | grep -q "vortex-db"; then
        print_color $YELLOW "⚠️  Oracle já está rodando!"
        print_color $GREEN "   🌐 Oracle: localhost:1521 (ORCLCDB/ORCLPDB1)"
        print_color $GREEN "   🔧 Enterprise Manager: http://localhost:5500/em"
        exit 0
    fi
    
    # Limpar containers antigos se existirem
    print_color $YELLOW "🧹 Limpando containers Oracle antigos..."
    docker-compose down -v 2>/dev/null || true
    
    # Iniciar Oracle
    print_color $BLUE "🚀 Iniciando Oracle Database..."
    docker-compose up -d db
    
    # Aguardar Oracle estar pronto
    print_color $BLUE "⏳ Aguardando Oracle estar completamente pronto..."
    print_color $YELLOW "   ⚠️  Primeira inicialização pode levar vários minutos..."
    
    local max_attempts=120  # 10 minutos
    local attempt=1
    
    while [[ $attempt -le $max_attempts ]]; do
        if docker ps --filter "name=vortex-db" --filter "health=healthy" | grep -q "vortex-db"; then
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
        ((attempt++))
    done
    
    print_color $GREEN "✅ Oracle iniciado com sucesso!"
    print_color $GREEN "   🌐 Oracle: localhost:1521"
    print_color $GREEN "   📊 SID: ORCLCDB"
    print_color $GREEN "   🔧 PDB: ORCLPDB1"
    print_color $GREEN "   👤 Usuário: system"
    print_color $GREEN "   🔐 Senha: Oracle_1234"
    print_color $GREEN "   🔧 Enterprise Manager: http://localhost:5500/em"
    
else
    print_color $RED "❌ Arquivo docker-compose.yml não encontrado!"
    print_color $YELLOW "💡 Esperado em: $DOCKER_DIR/docker-compose.yml"
    exit 1
fi 