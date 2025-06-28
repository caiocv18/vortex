#!/bin/bash

# Script para iniciar a aplicação Nexdom com Oracle Database
# Uso: ./start-oracle.sh [opcoes]

set -e

echo "🚀 Iniciando Nexdom com Oracle Database..."

# Verificar se Docker está rodando
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker não está rodando. Por favor, inicie o Docker primeiro."
    exit 1
fi

# Função para mostrar ajuda
show_help() {
    echo "Uso: $0 [OPÇÕES]"
    echo ""
    echo "Opções:"
    echo "  -h, --help     Mostrar esta ajuda"
    echo "  -d, --detach   Executar em background (detached mode)"
    echo "  -b, --build    Forçar rebuild das imagens"
    echo "  --db-only      Executar apenas o banco Oracle"
    echo "  --logs         Mostrar logs após iniciar"
    echo "  --stop         Parar todos os serviços"
    echo "  --clean        Parar e remover volumes (CUIDADO: apaga dados!)"
    echo ""
    echo "Exemplos:"
    echo "  $0                    # Iniciar todos os serviços"
    echo "  $0 -d                 # Iniciar em background"
    echo "  $0 --db-only          # Apenas Oracle"
    echo "  $0 --logs             # Iniciar e mostrar logs"
    echo "  $0 --stop             # Parar serviços"
}

# Processar argumentos
DETACH=""
BUILD=""
DB_ONLY=""
SHOW_LOGS=""
STOP=""
CLEAN=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -d|--detach)
            DETACH="-d"
            shift
            ;;
        -b|--build)
            BUILD="--build"
            shift
            ;;
        --db-only)
            DB_ONLY="db"
            shift
            ;;
        --logs)
            SHOW_LOGS="true"
            shift
            ;;
        --stop)
            STOP="true"
            shift
            ;;
        --clean)
            CLEAN="true"
            shift
            ;;
        *)
            echo "❌ Opção desconhecida: $1"
            show_help
            exit 1
            ;;
    esac
done

# Parar serviços se solicitado
if [[ "$STOP" == "true" ]]; then
    echo "🛑 Parando serviços..."
    docker-compose down
    echo "✅ Serviços parados."
    exit 0
fi

# Limpar volumes se solicitado
if [[ "$CLEAN" == "true" ]]; then
    echo "🧹 Parando serviços e removendo volumes..."
    echo "⚠️  ATENÇÃO: Todos os dados do banco serão perdidos!"
    read -p "Tem certeza? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose down -v
        echo "✅ Serviços parados e volumes removidos."
    else
        echo "❌ Operação cancelada."
    fi
    exit 0
fi

# Verificar se já existem containers rodando
if docker-compose ps | grep -q "Up"; then
    echo "⚠️  Alguns serviços já estão rodando."
    read -p "Deseja reiniciar? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🔄 Reiniciando serviços..."
        docker-compose down
    else
        echo "ℹ️  Mantendo serviços atuais."
        exit 0
    fi
fi

# Iniciar serviços
echo "📦 Iniciando serviços Docker..."
if [[ -n "$DB_ONLY" ]]; then
    echo "🗄️  Iniciando apenas Oracle Database..."
    docker-compose up $DETACH $BUILD $DB_ONLY
else
    echo "🚀 Iniciando Oracle Database + Aplicação Spring Boot..."
    docker-compose up $DETACH $BUILD
fi

# Mostrar informações após iniciar
if [[ "$DETACH" == "-d" ]]; then
    echo ""
    echo "✅ Serviços iniciados em background!"
    echo ""
    echo "📊 Status dos containers:"
    docker-compose ps
    echo ""
    echo "🌐 URLs da aplicação:"
    echo "   API: http://localhost:8080"
    echo "   Swagger UI: http://localhost:8080/swagger-ui.html"
    echo ""
    echo "🗄️  Conexão Oracle:"
    echo "   Host: localhost"
    echo "   Porta: 1521"
    echo "   SID: ORCLCDB"
    echo "   PDB: ORCLPDB1"
    echo "   Usuário: system"
    echo "   Senha: Oracle_1234"
    echo ""
    echo "📋 Comandos úteis:"
    echo "   docker logs nexdom-app -f    # Logs da aplicação"
    echo "   docker logs nexdom-db -f     # Logs do Oracle"
    echo "   docker-compose down          # Parar serviços"
    echo ""
fi

# Mostrar logs se solicitado
if [[ "$SHOW_LOGS" == "true" && "$DETACH" == "-d" ]]; then
    echo "📄 Mostrando logs da aplicação..."
    sleep 2
    docker logs nexdom-app -f
fi 