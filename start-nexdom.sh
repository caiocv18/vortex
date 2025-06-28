#!/bin/bash

# Script de inicialização completo do Nexdom
# Gerencia backend (Spring Boot) e frontend (Vue.js/Vite)
# Permite escolher entre ambiente de desenvolvimento (H2) ou produção (Oracle)

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Função para imprimir com cores
print_color() {
    echo -e "${1}${2}${NC}"
}

# Função para mostrar banner
show_banner() {
    print_color $CYAN "
╔═══════════════════════════════════════════════════════════════╗
║                                                               ║
║                    🚀 NEXDOM LAUNCHER 🚀                     ║
║                                                               ║
║              Sistema de Controle de Estoque                  ║
║                                                               ║
╚═══════════════════════════════════════════════════════════════╝
    "
}

# Função para mostrar ajuda
show_help() {
    echo "Uso: $0 [OPÇÕES]"
    echo ""
    echo "Opções:"
    echo "  -h, --help          Mostrar esta ajuda"
    echo "  -e, --env ENV       Definir ambiente (dev|prd)"
    echo "  -b, --backend-only  Executar apenas backend"
    echo "  -f, --frontend-only Executar apenas frontend"
    echo "  --stop              Parar todos os serviços"
    echo "  --clean             Limpar containers e volumes"
    echo "  --logs              Mostrar logs após iniciar"
    echo ""
    echo "Ambientes:"
    echo "  dev  - Desenvolvimento com H2 Database"
    echo "  prd  - Produção com Oracle Database"
    echo ""
    echo "Exemplos:"
    echo "  $0                     # Modo interativo"
    echo "  $0 -e dev              # Ambiente desenvolvimento"
    echo "  $0 -e prd --logs       # Ambiente produção com logs"
    echo "  $0 --backend-only      # Apenas backend"
    echo "  $0 --stop              # Parar serviços"
}

# Função para verificar pré-requisitos
check_prerequisites() {
    print_color $BLUE "🔍 Verificando pré-requisitos..."
    
    # Verificar Docker
    if ! command -v docker &> /dev/null; then
        print_color $RED "❌ Docker não encontrado. Por favor, instale o Docker."
        exit 1
    fi
    
    if ! docker info > /dev/null 2>&1; then
        print_color $RED "❌ Docker não está rodando. Por favor, inicie o Docker."
        exit 1
    fi
    
    # Verificar Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        print_color $RED "❌ Docker Compose não encontrado. Por favor, instale o Docker Compose."
        exit 1
    fi
    
    # Verificar Node.js (para frontend)
    if ! command -v node &> /dev/null; then
        print_color $YELLOW "⚠️  Node.js não encontrado. Frontend será executado via Docker."
        NODE_AVAILABLE=false
    else
        NODE_AVAILABLE=true
        NODE_VERSION=$(node --version)
        print_color $GREEN "✅ Node.js encontrado: $NODE_VERSION"
    fi
    
    # Verificar npm (para frontend)
    if ! command -v npm &> /dev/null; then
        print_color $YELLOW "⚠️  npm não encontrado. Frontend será executado via Docker."
        NPM_AVAILABLE=false
    else
        NPM_AVAILABLE=true
        NPM_VERSION=$(npm --version)
        print_color $GREEN "✅ npm encontrado: $NPM_VERSION"
    fi
    
    print_color $GREEN "✅ Docker encontrado e rodando"
}

# Função para escolher ambiente
choose_environment() {
    if [[ -n "$ENVIRONMENT" ]]; then
        return
    fi
    
    print_color $YELLOW "
🌍 Escolha o ambiente de execução:

1) 🔧 Desenvolvimento (dev)
   - Backend: Spring Boot
   - Database: H2 (em memória)
   - Frontend: Vite dev server
   - Hot reload habilitado

2) 🚀 Produção (prd)
   - Backend: Spring Boot
   - Database: Oracle (Docker)
   - Frontend: Build otimizado
   - Ambiente containerizado
"
    
    while true; do
        read -p "Digite sua escolha (1 para dev, 2 para prd): " choice
        case $choice in
            1)
                ENVIRONMENT="dev"
                print_color $GREEN "✅ Ambiente de desenvolvimento selecionado"
                break
                ;;
            2)
                ENVIRONMENT="prd"
                print_color $GREEN "✅ Ambiente de produção selecionado"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite 1 ou 2."
                ;;
        esac
    done
}

# Função para escolher componentes
choose_components() {
    if [[ "$BACKEND_ONLY" == "true" ]]; then
        RUN_BACKEND=true
        RUN_FRONTEND=false
        return
    fi
    
    if [[ "$FRONTEND_ONLY" == "true" ]]; then
        RUN_BACKEND=false
        RUN_FRONTEND=true
        return
    fi
    
    print_color $YELLOW "
📦 Escolha os componentes para executar:

1) 🔄 Completo (Backend + Frontend)
2) ⚙️  Apenas Backend
3) 🎨 Apenas Frontend
"
    
    while true; do
        read -p "Digite sua escolha (1, 2 ou 3): " choice
        case $choice in
            1)
                RUN_BACKEND=true
                RUN_FRONTEND=true
                print_color $GREEN "✅ Executando Backend + Frontend"
                break
                ;;
            2)
                RUN_BACKEND=true
                RUN_FRONTEND=false
                print_color $GREEN "✅ Executando apenas Backend"
                break
                ;;
            3)
                RUN_BACKEND=false
                RUN_FRONTEND=true
                print_color $GREEN "✅ Executando apenas Frontend"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite 1, 2 ou 3."
                ;;
        esac
    done
}

# Função para parar serviços
stop_services() {
    print_color $YELLOW "🛑 Parando todos os serviços..."
    
    # Parar containers Docker
    docker-compose -f docker-compose.full.yml down 2>/dev/null || true
    cd backend
    docker-compose down 2>/dev/null || true
    docker-compose -f docker-compose.dev.yml down 2>/dev/null || true
    cd ..
    
    # Parar processos Node.js (frontend)
    pkill -f "vite" 2>/dev/null || true
    pkill -f "npm run dev" 2>/dev/null || true
    pkill -f "npm run preview" 2>/dev/null || true
    
    # Parar processo Maven (backend dev)
    if [[ -f "backend.pid" ]]; then
        PID=$(cat backend.pid)
        kill $PID 2>/dev/null || true
        rm -f backend.pid
    fi
    
    # Parar processo frontend
    if [[ -f "frontend.pid" ]]; then
        PID=$(cat frontend.pid)
        kill $PID 2>/dev/null || true
        rm -f frontend.pid
    fi
    
    print_color $GREEN "✅ Todos os serviços foram parados."
}

# Função para limpar ambiente
clean_environment() {
    print_color $YELLOW "🧹 Limpando ambiente..."
    print_color $RED "⚠️  ATENÇÃO: Todos os dados do banco serão perdidos!"
    read -p "Tem certeza? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        stop_services
        cd backend
        docker-compose down -v 2>/dev/null || true
        docker-compose -f docker-compose.dev.yml down -v 2>/dev/null || true
        docker system prune -f
        cd ..
        print_color $GREEN "✅ Ambiente limpo."
    else
        print_color $YELLOW "❌ Operação cancelada."
    fi
}

# Função para executar backend em desenvolvimento
start_backend_dev() {
    print_color $BLUE "🔧 Iniciando Backend em modo desenvolvimento..."
    
    cd backend
    
    # Verificar se Maven está disponível
    if command -v mvn &> /dev/null; then
        print_color $GREEN "📦 Executando com Maven local..."
        export SPRING_PROFILES_ACTIVE=dev
        nohup mvn spring-boot:run > ../backend.log 2>&1 &
        BACKEND_PID=$!
        echo $BACKEND_PID > ../backend.pid
        print_color $GREEN "✅ Backend iniciado (PID: $BACKEND_PID)"
    else
        print_color $YELLOW "📦 Maven não encontrado, usando Docker..."
        # Criar docker-compose temporário para dev
        cat > docker-compose.dev.yml << EOF
version: '3.8'
services:
  nexdom-dev:
    build: .
    container_name: nexdom-app-dev
    environment:
      SPRING_PROFILES_ACTIVE: dev
    ports:
      - "8080:8080"
EOF
        docker-compose -f docker-compose.dev.yml up -d
        print_color $GREEN "✅ Backend iniciado no Docker"
    fi
    
    cd ..
}

# Função para executar backend em produção
start_backend_prd() {
    print_color $BLUE "🚀 Iniciando Backend em modo produção..."
    
    if [[ "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
        # Se frontend também deve rodar e npm não está disponível, usar compose completo
        print_color $YELLOW "📦 Usando Docker Compose completo (Backend + Oracle + Frontend)..."
        docker-compose -f docker-compose.full.yml up -d --build
        print_color $GREEN "✅ Stack completa iniciada"
    else
        # Apenas backend + Oracle
        cd backend
        docker-compose up -d --build
        print_color $GREEN "✅ Backend e Oracle iniciados"
        cd ..
    fi
}

# Função para executar frontend
start_frontend() {
    print_color $BLUE "🎨 Iniciando Frontend..."
    
    # Se já foi iniciado via Docker Compose completo, pular
    if [[ "$ENVIRONMENT" == "prd" && "$NPM_AVAILABLE" == "false" ]]; then
        print_color $GREEN "✅ Frontend já iniciado via Docker Compose"
        return 0
    fi
    
    cd frontend
    
    # Verificar se node_modules existe
    if [[ ! -d "node_modules" ]]; then
        if [[ "$NPM_AVAILABLE" == "true" ]]; then
            print_color $YELLOW "📦 Instalando dependências do frontend..."
            npm install
        else
            print_color $RED "❌ npm não disponível e node_modules não existe."
            print_color $YELLOW "💡 Frontend será executado via Docker na próxima execução"
            cd ..
            return 1
        fi
    fi
    
    if [[ "$ENVIRONMENT" == "dev" ]]; then
        if [[ "$NPM_AVAILABLE" == "true" ]]; then
            print_color $GREEN "🔥 Iniciando servidor de desenvolvimento Vite..."
            nohup npm run dev > ../frontend.log 2>&1 &
            FRONTEND_PID=$!
            echo $FRONTEND_PID > ../frontend.pid
            print_color $GREEN "✅ Frontend dev server iniciado (PID: $FRONTEND_PID)"
        else
            print_color $RED "❌ npm não disponível para executar frontend em desenvolvimento."
            cd ..
            return 1
        fi
    else
        print_color $GREEN "🏗️  Fazendo build do frontend para produção..."
        if [[ "$NPM_AVAILABLE" == "true" ]]; then
            npm run build
            print_color $GREEN "📦 Servindo frontend com preview..."
            nohup npm run preview > ../frontend.log 2>&1 &
            FRONTEND_PID=$!
            echo $FRONTEND_PID > ../frontend.pid
            print_color $GREEN "✅ Frontend preview iniciado (PID: $FRONTEND_PID)"
        else
            print_color $RED "❌ npm não disponível para build do frontend."
            cd ..
            return 1
        fi
    fi
    
    cd ..
}

# Função para mostrar status
show_status() {
    print_color $CYAN "
📊 STATUS DOS SERVIÇOS
═══════════════════════════════════════════════════════════════
"
    
    if [[ "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "🔧 BACKEND ($ENVIRONMENT):"
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            if [[ -f "backend.pid" ]]; then
                PID=$(cat backend.pid)
                if ps -p $PID > /dev/null 2>&1; then
                    print_color $GREEN "   ✅ Rodando (PID: $PID)"
                    print_color $GREEN "   🌐 API: http://localhost:8080"
                    print_color $GREEN "   📚 Swagger: http://localhost:8080/swagger-ui.html"
                    print_color $GREEN "   🗄️  H2 Console: http://localhost:8080/h2-console"
                else
                    print_color $RED "   ❌ Não está rodando"
                fi
            else
                if docker ps | grep -q "nexdom-app-dev"; then
                    print_color $GREEN "   ✅ Rodando no Docker"
                    print_color $GREEN "   🌐 API: http://localhost:8080"
                    print_color $GREEN "   📚 Swagger: http://localhost:8080/swagger-ui.html"
                else
                    print_color $RED "   ❌ Não está rodando"
                fi
            fi
        else
            if docker ps | grep -q "nexdom-app"; then
                print_color $GREEN "   ✅ Rodando no Docker"
                print_color $GREEN "   🌐 API: http://localhost:8080"
                print_color $GREEN "   📚 Swagger: http://localhost:8080/swagger-ui.html"
                print_color $GREEN "   🗄️  Oracle: localhost:1521 (ORCLCDB/ORCLPDB1)"
            else
                print_color $RED "   ❌ Não está rodando"
            fi
        fi
    fi
    
    if [[ "$RUN_FRONTEND" == "true" ]]; then
        print_color $BLUE "🎨 FRONTEND ($ENVIRONMENT):"
        
        # Verificar se está rodando via Docker
        if docker ps | grep -q "nexdom-frontend"; then
            print_color $GREEN "   ✅ Rodando no Docker"
            print_color $GREEN "   🌐 App: http://localhost:3000"
        elif [[ -f "frontend.pid" ]]; then
            PID=$(cat frontend.pid)
            if ps -p $PID > /dev/null 2>&1; then
                print_color $GREEN "   ✅ Rodando (PID: $PID)"
                if [[ "$ENVIRONMENT" == "dev" ]]; then
                    print_color $GREEN "   🌐 App: http://localhost:5173"
                else
                    print_color $GREEN "   🌐 App: http://localhost:4173"
                fi
            else
                print_color $RED "   ❌ Não está rodando"
            fi
        else
            print_color $RED "   ❌ Não está rodando"
        fi
    fi
    
    print_color $CYAN "
📋 COMANDOS ÚTEIS:
═══════════════════════════════════════════════════════════════"
    print_color $YELLOW "   ./start-nexdom.sh --stop     # Parar todos os serviços"
    print_color $YELLOW "   ./start-nexdom.sh --clean    # Limpar ambiente"
    print_color $YELLOW "   docker logs nexdom-app -f    # Logs do backend (prd)"
    print_color $YELLOW "   docker logs nexdom-db -f     # Logs do Oracle"
    print_color $YELLOW "   tail -f backend.log          # Logs do backend (dev)"
    print_color $YELLOW "   tail -f frontend.log         # Logs do frontend"
}

# Função para mostrar logs
show_logs() {
    if [[ "$ENVIRONMENT" == "prd" && "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "📄 Mostrando logs do backend (produção)..."
        sleep 2
        docker logs nexdom-app -f
    elif [[ "$ENVIRONMENT" == "dev" && "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "📄 Mostrando logs do backend (desenvolvimento)..."
        sleep 2
        if [[ -f "backend.log" ]]; then
            tail -f backend.log
        else
            docker logs nexdom-app-dev -f 2>/dev/null || echo "Logs não disponíveis"
        fi
    fi
}

# Função principal
main() {
    show_banner
    
    # Processar argumentos
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -e|--env)
                ENVIRONMENT="$2"
                shift 2
                ;;
            -b|--backend-only)
                BACKEND_ONLY="true"
                shift
                ;;
            -f|--frontend-only)
                FRONTEND_ONLY="true"
                shift
                ;;
            --stop)
                stop_services
                exit 0
                ;;
            --clean)
                clean_environment
                exit 0
                ;;
            --logs)
                SHOW_LOGS="true"
                shift
                ;;
            *)
                print_color $RED "❌ Opção desconhecida: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # Validar ambiente se fornecido
    if [[ -n "$ENVIRONMENT" && "$ENVIRONMENT" != "dev" && "$ENVIRONMENT" != "prd" ]]; then
        print_color $RED "❌ Ambiente inválido: $ENVIRONMENT. Use 'dev' ou 'prd'."
        exit 1
    fi
    
    # Verificar pré-requisitos
    check_prerequisites
    
    # Escolher ambiente se não fornecido
    choose_environment
    
    # Escolher componentes
    choose_components
    
    # Parar serviços existentes
    print_color $YELLOW "🔄 Verificando serviços existentes..."
    stop_services
    
    # Iniciar serviços
    print_color $BLUE "🚀 Iniciando serviços..."
    
    if [[ "$RUN_BACKEND" == "true" ]]; then
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            start_backend_dev
        else
            start_backend_prd
        fi
        
        print_color $GREEN "⏳ Aguardando backend inicializar..."
        sleep 8
    fi
    
    if [[ "$RUN_FRONTEND" == "true" ]]; then
        start_frontend
        
        print_color $GREEN "⏳ Aguardando frontend inicializar..."
        sleep 5
    fi
    
    # Mostrar status
    show_status
    
    # Mostrar logs se solicitado
    if [[ "$SHOW_LOGS" == "true" ]]; then
        show_logs
    fi
    
    print_color $GREEN "
🎉 Nexdom iniciado com sucesso!
═══════════════════════════════════════════════════════════════

Para parar os serviços, execute: ./start-nexdom.sh --stop
"
}

# Variáveis globais
ENVIRONMENT=""
BACKEND_ONLY="false"
FRONTEND_ONLY="false"
RUN_BACKEND="true"
RUN_FRONTEND="true"
SHOW_LOGS="false"
NODE_AVAILABLE="false"
NPM_AVAILABLE="false"

# Executar função principal
main "$@" 