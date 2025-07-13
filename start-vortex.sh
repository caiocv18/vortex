#!/bin/bash

# Script de inicialização completo do Vortex
# Gerencia backend (Spring Boot) e frontend (Vue.js/Vite)
# Permite escolher entre ambiente de desenvolvimento (H2) ou produção (Oracle)
#
# CORREÇÕES IMPLEMENTADAS:
# - Kafka agora é parado corretamente em todas as configurações
# - Função melhorada para aguardar Kafka estar completamente pronto
# - Backend em produção agora verifica se Kafka está pronto antes de iniciar
# - Parada de serviços mais robusta com verificação de containers órfãos
# - Tempos de espera aumentados para inicialização mais confiável
# - Banner do terminal corrigido

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
    print_color $CYAN "╔═══════════════════════════════════════════════════════════════╗"
    print_color $CYAN "║                                                               ║"
    print_color $CYAN "║                    🚀 VORTEX LAUNCHER 🚀                      ║"
    print_color $CYAN "║                                                               ║"
    print_color $CYAN "║              Sistema de Controle de Estoque                   ║"
    print_color $CYAN "║                                                               ║"
    print_color $CYAN "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
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
    echo "  -c, --components C  Definir componentes (full|backend|frontend)"
    echo "  -m, --messaging MSG Definir sistema de mensageria (kafka|rabbitmq|sqs|none)"
    echo "  --stop              Parar todos os serviços"
    echo "  --clean             Limpar containers e volumes"
    echo "  --fix-kafka         Executar correção automática do Kafka"
    echo "  --logs              Mostrar logs após iniciar"
    echo "  --no-interaction    Modo não interativo (usa valores padrão)"
    echo ""
    echo "Ambientes:"
    echo "  dev  - Desenvolvimento com H2 Database"
    echo "  prd  - Produção com Oracle Database"
    echo ""
    echo "Sistemas de Mensageria:"
    echo "  kafka    - Apache Kafka para event streaming"
    echo "  rabbitmq - RabbitMQ para mensageria tradicional"
    echo "  sqs      - Amazon SQS para processamento assíncrono"
    echo "  none     - Sem sistema de mensageria"
    echo ""
    echo "Componentes:"
    echo "  full     - Backend + Frontend (padrão)"
    echo "  backend  - Apenas Backend"
    echo "  frontend - Apenas Frontend"
    echo ""
    echo "Exemplos:"
    echo "  $0                                    # Modo interativo"
    echo "  $0 -e dev -m kafka -c full           # Desenvolvimento com Kafka (completo)"
    echo "  $0 -e dev -m rabbitmq -c backend     # Desenvolvimento com RabbitMQ (só backend)"
    echo "  $0 -e prd -m kafka -c full --logs    # Produção com Kafka"
    echo "  $0 -e dev -m none -c backend         # Desenvolvimento sem mensageria"
    echo "  $0 --backend-only -m sqs             # Apenas backend com SQS"
    echo "  $0 --stop                            # Parar serviços"
    echo "  $0 --fix-kafka                       # Corrigir problemas do Kafka"
    echo "  $0 --no-interaction -e dev -m kafka  # Modo não interativo"
}

# Função para verificar pré-requisitos
check_prerequisites() {
    print_color $BLUE "🔍 Verificando pré-requisitos..."
    
    # Verificar se diretórios dos serviços existem
    if [[ ! -d "backend/vortex-application-service" ]]; then
        print_color $RED "❌ Diretório backend/vortex-application-service não encontrado."
        exit 1
    fi
    
    if [[ ! -d "backend/vortex-authorization-service" ]]; then
        print_color $RED "❌ Diretório backend/vortex-authorization-service não encontrado."
        exit 1
    fi
    
    if [[ ! -d "frontend/vortex-application-service" ]]; then
        print_color $RED "❌ Diretório frontend/vortex-application-service não encontrado."
        exit 1
    fi
    
    if [[ ! -d "frontend/vortex-authorization-service" ]]; then
        print_color $RED "❌ Diretório frontend/vortex-authorization-service não encontrado."
        exit 1
    fi
    
    # Verificar conflitos de porta usando o script dedicado
    if [[ -f "scripts/check-ports.sh" ]]; then
        print_color $BLUE "🚢 Verificando conflitos de porta conforme PORTS.md..."
        chmod +x scripts/check-ports.sh
        ./scripts/check-ports.sh --auto-kill 2>/dev/null || true
    fi
    
    # Verificar se há problemas conhecidos do Kafka
    if [[ -f "logs/backend.log" ]] && grep -q "kafka:29092.*DNS resolution failed" logs/backend.log 2>/dev/null; then
        print_color $YELLOW "⚠️  Detectado problema conhecido do Kafka (DNS resolution)"
        print_color $YELLOW "💡 Execute: ./start-vortex.sh --fix-kafka"
    fi
    
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

# Função para escolher cor do tema
choose_color_theme() {
    if [[ -n "$COLOR_THEME" ]]; then
        return
    fi
    
    # Modo não interativo usa verde por padrão (tema original)
    if [[ "$NO_INTERACTION" == "true" ]]; then
        COLOR_THEME="verde"
        print_color $GREEN "✅ Tema verde selecionado (padrão)"
        return
    fi
    
    print_color $CYAN "
🎨 ESCOLHA A COR DO TEMA DA APLICAÇÃO:

1) 🔴 Vermelho     2) 🟢 Verde        3) 🔵 Azul         4) 🟠 Laranja
5) 🟣 Roxo         6) 🩷 Rosa         7) 🔷 Azul Claro   8) 🔹 Azul Escuro  
9) 🟡 Amarelo     10) ⚫ Cinza       11) ⚫ Preto       12) ⚪ Branco
"
    
    while true; do
        read -p "Digite sua escolha (1-12): " choice
        case $choice in
            1)
                COLOR_THEME="vermelho"
                print_color $RED "✅ Tema vermelho selecionado"
                break
                ;;
            2)
                COLOR_THEME="verde"
                print_color $GREEN "✅ Tema verde selecionado"
                break
                ;;
            3)
                COLOR_THEME="azul"
                print_color $BLUE "✅ Tema azul selecionado"
                break
                ;;
            4)
                COLOR_THEME="laranja"
                print_color $YELLOW "✅ Tema laranja selecionado"
                break
                ;;
            5)
                COLOR_THEME="roxo"
                print_color $PURPLE "✅ Tema roxo selecionado"
                break
                ;;
            6)
                COLOR_THEME="rosa"
                print_color $PURPLE "✅ Tema rosa selecionado"
                break
                ;;
            7)
                COLOR_THEME="azulClaro"
                print_color $CYAN "✅ Tema azul claro selecionado"
                break
                ;;
            8)
                COLOR_THEME="azulEscuro"
                print_color $BLUE "✅ Tema azul escuro selecionado"
                break
                ;;
            9)
                COLOR_THEME="amarelo"
                print_color $YELLOW "✅ Tema amarelo selecionado"
                break
                ;;
            10)
                COLOR_THEME="cinza"
                print_color $NC "✅ Tema cinza selecionado"
                break
                ;;
            11)
                COLOR_THEME="preto"
                print_color $NC "✅ Tema preto selecionado"
                break
                ;;
            12)
                COLOR_THEME="branco"
                print_color $NC "✅ Tema branco selecionado"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite um número de 1 a 12."
                ;;
        esac
    done
}

# Função para escolher ambiente
choose_environment() {
    if [[ -n "$ENVIRONMENT" ]]; then
        return
    fi
    
    # Modo não interativo usa desenvolvimento por padrão
    if [[ "$NO_INTERACTION" == "true" ]]; then
        ENVIRONMENT="dev"
        print_color $GREEN "✅ Ambiente de desenvolvimento selecionado (padrão)"
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

# Função para escolher sistema de mensageria
choose_messaging() {
    if [[ -n "$MESSAGING_SYSTEM" ]]; then
        return
    fi
    
    # Modo não interativo usa Kafka por padrão
    if [[ "$NO_INTERACTION" == "true" ]]; then
        MESSAGING_SYSTEM="kafka"
        print_color $GREEN "✅ Apache Kafka selecionado (padrão)"
        return
    fi
    
    print_color $YELLOW "
📨 Escolha o sistema de mensageria:

1) 🚀 Apache Kafka
   - Event Sourcing
   - Streaming em tempo real
   - Alta performance
   - Replay de eventos

2) 🐰 RabbitMQ
   - Mensageria tradicional
   - Filas confiáveis
   - Protocolo AMQP
   - Interface de gerenciamento

3) ☁️  Amazon SQS
   - Processamento assíncrono
   - Gerenciado pela AWS
   - Simplicidade de uso
   - Pay-per-use

4) ❌ Nenhum
   - Processamento síncrono apenas
   - Modo simplificado
"
    
    while true; do
        read -p "Digite sua escolha (1, 2, 3 ou 4): " choice
        case $choice in
            1)
                MESSAGING_SYSTEM="kafka"
                print_color $GREEN "✅ Apache Kafka selecionado"
                break
                ;;
            2)
                MESSAGING_SYSTEM="rabbitmq"
                print_color $GREEN "✅ RabbitMQ selecionado"
                break
                ;;
            3)
                MESSAGING_SYSTEM="sqs"
                print_color $GREEN "✅ Amazon SQS selecionado"
                break
                ;;
            4)
                MESSAGING_SYSTEM="none"
                print_color $GREEN "✅ Nenhum sistema de mensageria selecionado"
                break
                ;;
            *)
                print_color $RED "❌ Opção inválida. Digite 1, 2, 3 ou 4."
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
    
    # Usar parâmetro -c se fornecido
    if [[ -n "$COMPONENTS" ]]; then
        case $COMPONENTS in
            "full")
                RUN_BACKEND=true
                RUN_FRONTEND=true
                print_color $GREEN "✅ Executando Backend + Frontend"
                return
                ;;
            "backend")
                RUN_BACKEND=true
                RUN_FRONTEND=false
                print_color $GREEN "✅ Executando apenas Backend"
                return
                ;;
            "frontend")
                RUN_BACKEND=false
                RUN_FRONTEND=true
                print_color $GREEN "✅ Executando apenas Frontend"
                return
                ;;
        esac
    fi
    
    # Modo não interativo usa completo por padrão
    if [[ "$NO_INTERACTION" == "true" ]]; then
        RUN_BACKEND=true
        RUN_FRONTEND=true
        print_color $GREEN "✅ Executando Backend + Frontend (padrão)"
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
    
    # Parar containers Docker em ordem específica para evitar problemas
    # 1. Primeiro parar aplicações que dependem do Kafka
    docker-compose -f infra/docker/docker-compose.full.yml down 2>/dev/null || true
    docker-compose -f infra/docker/docker-compose.full-kafka.yml down 2>/dev/null || true
    
    # 2. Parar backend específico
    cd infra/docker 2>/dev/null || true
    docker-compose down 2>/dev/null || true
    docker-compose -f docker-compose.dev.yml down 2>/dev/null || true
    cd ../.. 2>/dev/null || true
    
    # 3. Parar serviços de autorização
    docker-compose -f infra/docker/docker-compose.auth.yml down 2>/dev/null || true
    docker stop vortex-auth-service vortex-auth-frontend 2>/dev/null || true
    docker rm vortex-auth-service vortex-auth-frontend 2>/dev/null || true
    
    # 3. Parar Kafka, RabbitMQ e Zookeeper (todas as configurações possíveis)
    docker-compose -f infra/docker/docker-compose.kafka-simple.yml down 2>/dev/null || true
    docker-compose -f infra/docker/docker-compose.kafka.yml down 2>/dev/null || true
    docker-compose -f infra/docker/docker-compose.rabbitmq.yml down 2>/dev/null || true
    
    # 4. Forçar parada de containers específicos se ainda estiverem rodando
    docker stop vortex-kafka-simple vortex-zookeeper-simple vortex-kafka-ui-simple 2>/dev/null || true
    docker stop vortex-kafka vortex-zookeeper vortex-kafka-ui 2>/dev/null || true
    docker stop vortex-rabbitmq 2>/dev/null || true
    docker stop vortex-app vortex-app-dev vortex-db vortex-frontend 2>/dev/null || true
    docker stop vortex-auth-service vortex-auth-frontend 2>/dev/null || true
    
    # 5. Remover containers órfãos
    docker rm vortex-kafka-simple vortex-zookeeper-simple vortex-kafka-ui-simple 2>/dev/null || true
    docker rm vortex-kafka vortex-zookeeper vortex-kafka-ui 2>/dev/null || true
    docker rm vortex-rabbitmq 2>/dev/null || true
    docker rm vortex-app vortex-app-dev vortex-db vortex-frontend 2>/dev/null || true
    docker rm vortex-auth-service vortex-auth-frontend 2>/dev/null || true
    
    # 6. Parar processos Node.js (frontend)
    pkill -f "vite" 2>/dev/null || true
    pkill -f "npm run dev" 2>/dev/null || true
    pkill -f "npm run preview" 2>/dev/null || true
    
    # 7. Parar processos Java/Maven mais agressivamente
    pkill -f "mvn.*quarkus:dev" 2>/dev/null || true
    pkill -f "java.*quarkus" 2>/dev/null || true
    
    # 8. Parar processo Maven (backend dev)
    if [[ -f "logs/backend.pid" ]]; then
        PID=$(cat logs/backend.pid)
        kill -9 $PID 2>/dev/null || true
        rm -f logs/backend.pid
    fi
    
    # 9. Parar processo frontend
    if [[ -f "logs/frontend.pid" ]]; then
        PID=$(cat logs/frontend.pid)
        kill -9 $PID 2>/dev/null || true
        rm -f logs/frontend.pid
    fi
    
    # 10. Parar processo backend de autorização
    if [[ -f "logs/auth-backend.pid" ]]; then
        PID=$(cat logs/auth-backend.pid)
        kill -9 $PID 2>/dev/null || true
        rm -f logs/auth-backend.pid
    fi
    
    # 11. Parar processo frontend de autorização
    if [[ -f "logs/auth-frontend.pid" ]]; then
        PID=$(cat logs/auth-frontend.pid)
        kill -9 $PID 2>/dev/null || true
        rm -f logs/auth-frontend.pid
    fi
    
    # 12. Limpar redes Docker órfãs relacionadas ao Vortex
    docker network rm vortex-kafka-network 2>/dev/null || true
    docker network rm vortex-rabbitmq-network 2>/dev/null || true
    docker network rm vortex_default 2>/dev/null || true
    
    # 13. Aguardar um pouco para garantir que todos os containers foram parados
    sleep 3
    
    # 14. Verificar se ainda há containers do Vortex rodando
    local remaining_containers=$(docker ps --filter "name=vortex" --format "{{.Names}}" | wc -l)
    if [[ $remaining_containers -gt 0 ]]; then
        print_color $YELLOW "⚠️  Ainda há $remaining_containers container(s) rodando:"
        docker ps --filter "name=vortex" --format "table {{.Names}}\t{{.Status}}"
        print_color $YELLOW "💡 Forçando parada..."
        docker ps --filter "name=vortex" -q | xargs -r docker stop
        docker ps --filter "name=vortex" -q | xargs -r docker rm
    fi
    
    print_color $GREEN "✅ Todos os serviços foram parados."
}

# Função para corrigir problemas do Kafka
fix_kafka_issues() {
    print_color $BLUE "🔧 Executando correção automática do Kafka..."
    
    if [[ -f "infra/kafka/fix-kafka-issues.sh" ]]; then
        chmod +x infra/kafka/fix-kafka-issues.sh
        ./infra/kafka/fix-kafka-issues.sh
        
        if [[ $? -eq 0 ]]; then
            print_color $GREEN "✅ Correção do Kafka concluída com sucesso!"
            print_color $GREEN "🌐 Kafka UI disponível em: http://localhost:8090"
        else
            print_color $RED "❌ Falha na correção do Kafka"
            exit 1
        fi
    else
        print_color $RED "❌ Script fix-kafka-issues.sh não encontrado!"
        print_color $YELLOW "💡 Certifique-se de que o arquivo está em infra/kafka/"
        exit 1
    fi
}

# Função para limpar ambiente
clean_environment() {
    print_color $YELLOW "🧹 Limpando ambiente..."
    print_color $RED "⚠️  ATENÇÃO: Todos os dados do banco e Kafka serão perdidos!"
    read -p "Tem certeza? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        stop_services
        cd infra/docker
        docker-compose down -v 2>/dev/null || true
        docker-compose -f docker-compose.dev.yml down -v 2>/dev/null || true
        cd ../..
        docker-compose -f infra/docker/docker-compose.kafka.yml down -v 2>/dev/null || true
        docker-compose -f infra/docker/docker-compose.kafka-simple.yml down -v 2>/dev/null || true
        docker-compose -f infra/docker/docker-compose.full.yml down -v 2>/dev/null || true
        docker-compose -f infra/docker/docker-compose.full-kafka.yml down -v 2>/dev/null || true
        
        # Remover volumes específicos do Kafka se existirem
        docker volume rm vortex_kafka-simple-data 2>/dev/null || true
        docker volume rm vortex_kafka-data 2>/dev/null || true
        docker volume rm vortex_zookeeper-data 2>/dev/null || true
        docker volume rm vortex_zookeeper-logs 2>/dev/null || true
        
        docker system prune -f
        print_color $GREEN "✅ Ambiente limpo."
    else
        print_color $YELLOW "❌ Operação cancelada."
    fi
}

# Função para aguardar Kafka estar pronto
wait_for_kafka() {
    local max_attempts=60
    local attempt=1
    
    print_color $BLUE "⏳ Aguardando Kafka estar completamente pronto..."
    
    while [[ $attempt -le $max_attempts ]]; do
        # Verificar se o container está saudável
        if docker ps --filter "name=vortex-kafka" --filter "health=healthy" | grep -q "vortex-kafka"; then
            print_color $GREEN "✅ Kafka está saudável!"
            
            # Verificar se consegue conectar no broker
            if docker exec vortex-kafka-simple kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1 || \
               docker exec vortex-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; then
                print_color $GREEN "✅ Kafka broker está respondendo!"
                return 0
            fi
        fi
        
        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando Kafka ficar pronto"
            return 1
        fi
        
        print_color $YELLOW "   ⏳ Aguardando Kafka... ($attempt/$max_attempts)"
        sleep 2
        ((attempt++))
    done
}

# Função para iniciar Kafka
start_kafka() {
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        # Se vai usar o compose completo com Kafka, não iniciar separadamente
        if [[ "$ENVIRONMENT" == "prd" && "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
            print_color $BLUE "🚀 Kafka será iniciado junto com a stack completa..."
            return 0
        fi
        
        print_color $BLUE "🚀 Iniciando Apache Kafka..."
        
        # Verificar se arquivo kafka-simple existe (nova configuração corrigida)
        if [[ -f "infra/docker/docker-compose.kafka-simple.yml" ]]; then
            print_color $GREEN "📦 Usando configuração Kafka simplificada (corrigida)..."
            
            # Limpar containers antigos se existirem
            print_color $YELLOW "🧹 Limpando containers Kafka antigos..."
            docker-compose -f infra/docker/docker-compose.kafka-simple.yml down -v 2>/dev/null || true
            docker-compose -f infra/docker/docker-compose.kafka.yml down -v 2>/dev/null || true
            
            # Verificar se portas estão livres
            if lsof -Pi :9092 -sTCP:LISTEN -t >/dev/null 2>&1; then
                print_color $YELLOW "⚠️  Porta 9092 ocupada. Tentando liberar..."
                # Tentar parar processo que está usando a porta
                pkill -f "kafka" 2>/dev/null || true
                sleep 2
            fi
            
            if lsof -Pi :2181 -sTCP:LISTEN -t >/dev/null 2>&1; then
                print_color $YELLOW "⚠️  Porta 2181 ocupada. Tentando liberar..."
                pkill -f "zookeeper" 2>/dev/null || true
                sleep 2
            fi
            
            # Iniciar Kafka com configuração simplificada
            docker-compose -f infra/docker/docker-compose.kafka-simple.yml up -d
            
            # Usar função centralizada para aguardar Kafka
            if ! wait_for_kafka; then
                print_color $YELLOW "💡 Executando correção automática..."
                
                # Executar script de correção se disponível
                if [[ -f "infra/kafka/fix-kafka-issues.sh" ]]; then
                    chmod +x infra/kafka/fix-kafka-issues.sh
                    ./infra/kafka/fix-kafka-issues.sh
                    return $?
                else
                    return 1
                fi
            fi
            
            # Criar tópicos necessários
            print_color $BLUE "📋 Criando tópicos necessários..."
            sleep 2
            
            local topics=("movimento-estoque" "produto-events" "alerta-estoque" "auditoria-events")
            for topic in "${topics[@]}"; do
                docker exec vortex-kafka-simple kafka-topics --bootstrap-server localhost:9092 --create --topic "$topic" --partitions 3 --replication-factor 1 --if-not-exists 2>/dev/null || true
            done
            
            # Verificar tópicos criados
            print_color $GREEN "📊 Tópicos disponíveis:"
            docker exec vortex-kafka-simple kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | sed 's/^/   ✓ /'
            
        elif [[ -f "infra/docker/docker-compose.kafka.yml" ]]; then
            print_color $YELLOW "📦 Usando configuração Kafka legada..."
            # Verificar se arquivo existe
            docker-compose -f infra/docker/docker-compose.kafka.yml up -d
            print_color $GREEN "⏳ Aguardando Kafka inicializar..."
            sleep 15
        else
            print_color $RED "❌ Nenhum arquivo de configuração Kafka encontrado!"
            print_color $YELLOW "💡 Arquivos esperados: infra/docker/docker-compose.kafka-simple.yml ou infra/docker/docker-compose.kafka.yml"
            return 1
        fi
        
        # Verificar se Kafka está rodando
        if docker ps | grep -q "vortex-kafka"; then
            print_color $GREEN "✅ Kafka iniciado com sucesso"
            print_color $GREEN "   🌐 Kafka UI: http://localhost:8090"
            print_color $GREEN "   📡 Kafka Broker: localhost:9092"
            
            # Definir variáveis de ambiente para outros serviços
            export KAFKA_ENABLED=true
            export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
        else
            print_color $RED "❌ Falha ao iniciar Kafka"
            print_color $YELLOW "💡 Tente executar: ./infra/kafka/fix-kafka-issues.sh"
            return 1
        fi
    fi
}

# Função para iniciar RabbitMQ
start_rabbitmq() {
    if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
        # Se vai usar o compose completo com RabbitMQ, não iniciar separadamente
        if [[ "$ENVIRONMENT" == "prd" && "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
            print_color $BLUE "🐰 RabbitMQ será iniciado junto com a stack completa..."
            return 0
        fi
        
        print_color $BLUE "🐰 Iniciando RabbitMQ..."
        
        # Verificar se arquivo RabbitMQ existe
        if [[ -f "infra/docker/docker-compose.rabbitmq.yml" ]]; then
            print_color $GREEN "📦 Usando configuração RabbitMQ..."
            
            # Limpar containers antigos se existirem
            print_color $YELLOW "🧹 Limpando containers RabbitMQ antigos..."
            docker-compose -f infra/docker/docker-compose.rabbitmq.yml down -v 2>/dev/null || true
            
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
            docker-compose -f infra/docker/docker-compose.rabbitmq.yml up -d
            
            # Aguardar RabbitMQ estar pronto
            if ! wait_for_rabbitmq; then
                print_color $RED "❌ Falha ao iniciar RabbitMQ"
                return 1
            fi
            
        else
            print_color $RED "❌ Arquivo de configuração RabbitMQ não encontrado!"
            print_color $YELLOW "💡 Arquivo esperado: infra/docker/docker-compose.rabbitmq.yml"
            return 1
        fi
        
        # Verificar se RabbitMQ está rodando
        if docker ps | grep -q "vortex-rabbitmq"; then
            print_color $GREEN "✅ RabbitMQ iniciado com sucesso"
            print_color $GREEN "   🌐 Management UI: http://localhost:15672"
            print_color $GREEN "   📡 AMQP Port: localhost:5672"
            print_color $GREEN "   👤 Usuário: vortex / Senha: vortex123"
            
            # Definir variáveis de ambiente para outros serviços
            export RABBITMQ_ENABLED=true
            export SPRING_RABBITMQ_HOST=localhost
            export SPRING_RABBITMQ_PORT=5672
            export SPRING_RABBITMQ_USERNAME=vortex
            export SPRING_RABBITMQ_PASSWORD=vortex123
        else
            print_color $RED "❌ Falha ao iniciar RabbitMQ"
            return 1
        fi
    fi
}

# Função para aguardar RabbitMQ estar pronto
wait_for_rabbitmq() {
    print_color $BLUE "⏳ Aguardando RabbitMQ estar pronto..."
    
    local max_attempts=30
    local attempt=1
    
    while [[ $attempt -le $max_attempts ]]; do
        if docker exec vortex-rabbitmq rabbitmq-diagnostics status >/dev/null 2>&1; then
            print_color $GREEN "✅ RabbitMQ está pronto!"
            return 0
        fi
        
        print_color $YELLOW "⏳ Tentativa $attempt/$max_attempts - Aguardando RabbitMQ..."
        sleep 3
        ((attempt++))
    done
    
    print_color $RED "❌ Timeout aguardando RabbitMQ"
    return 1
}

# Função para aguardar Oracle estar pronto
wait_for_oracle() {
    local max_attempts=60
    local attempt=1
    
    print_color $BLUE "⏳ Aguardando Oracle Database estar pronto..."
    
    while [[ $attempt -le $max_attempts ]]; do
        if docker logs vortex-db 2>&1 | grep -q "DATABASE IS READY TO USE"; then
            print_color $GREEN "✅ Oracle Database está pronto!"
            return 0
        fi
        
        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando Oracle Database"
            return 1
        fi
        
        print_color $YELLOW "   ⏳ Aguardando Oracle Database... ($attempt/$max_attempts)"
        sleep 5
        ((attempt++))
    done
}

# Função para aguardar PostgreSQL estar pronto
wait_for_postgresql() {
    local max_attempts=30
    local attempt=1
    
    print_color $BLUE "⏳ Aguardando PostgreSQL estar pronto..."
    
    while [[ $attempt -le $max_attempts ]]; do
        if docker exec vortex-auth-db pg_isready -U vortex_auth -d vortex_auth >/dev/null 2>&1; then
            print_color $GREEN "✅ PostgreSQL está pronto!"
            return 0
        fi
        
        if [[ $attempt -eq $max_attempts ]]; then
            print_color $RED "❌ Timeout aguardando PostgreSQL"
            return 1
        fi
        
        print_color $YELLOW "   ⏳ Aguardando PostgreSQL... ($attempt/$max_attempts)"
        sleep 2
        ((attempt++))
    done
}

# Função para iniciar bancos de dados
start_databases() {
    print_color $BLUE "🗄️  Iniciando bancos de dados..."
    
    local databases_started=false
    
    # SEMPRE iniciar PostgreSQL para serviço de autorização (tanto dev quanto prd)
    print_color $BLUE "🐘 Iniciando PostgreSQL para serviço de autorização..."
    
    # Verificar se o container já existe
    if docker ps -a --format "{{.Names}}" | grep -q "^vortex-auth-db$"; then
        if ! docker ps --format "{{.Names}}" | grep -q "^vortex-auth-db$"; then
            print_color $YELLOW "🔄 PostgreSQL já existe mas está parado. Iniciando..."
            docker start vortex-auth-db
        else
            print_color $GREEN "✅ PostgreSQL já está rodando"
        fi
    else
        # Verificar se existe docker-compose para auth e criar o container
        if [[ -f "infra/docker/docker-compose.auth.yml" ]]; then
            print_color $BLUE "📦 Criando e iniciando PostgreSQL via docker-compose..."
            docker-compose -f infra/docker/docker-compose.auth.yml up -d auth-db
        else
            print_color $RED "❌ Arquivo docker-compose.auth.yml não encontrado!"
            return 1
        fi
    fi
    
    # Aguardar PostgreSQL estar pronto
    if docker ps --format "{{.Names}}" | grep -q "^vortex-auth-db$"; then
        wait_for_postgresql
        databases_started=true
        
        # Verificar configuração de porta
        print_color $BLUE "🔗 Verificando configuração de porta..."
        local port_mapping=$(docker port vortex-auth-db 2>/dev/null | grep "5432/tcp" | head -1 | cut -d':' -f2)
        if [[ -n "$port_mapping" ]]; then
            print_color $GREEN "✅ PostgreSQL mapeado para porta $port_mapping"
        else
            print_color $YELLOW "⚠️  Aviso: Mapeamento de porta não encontrado"
        fi
    else
        print_color $RED "❌ Falha ao iniciar PostgreSQL"
        return 1
    fi
    
    # Iniciar Oracle para ambiente de produção
    if [[ "$ENVIRONMENT" == "prd" ]]; then
        print_color $BLUE "🏛️  Iniciando Oracle Database..."
        
        # Verificar se o container já existe
        if docker ps -a --format "{{.Names}}" | grep -q "^vortex-db$"; then
            if ! docker ps --format "{{.Names}}" | grep -q "^vortex-db$"; then
                print_color $YELLOW "🔄 Oracle já existe mas está parado. Iniciando..."
                docker start vortex-db
            else
                print_color $GREEN "✅ Oracle já está rodando"
            fi
        else
            # Iniciar Oracle via docker-compose
            if [[ -f "infra/docker/docker-compose.yml" ]]; then
                cd infra/docker
                docker-compose up -d vortex-db 2>/dev/null || true
                cd ../..
            elif [[ -f "infra/docker/docker-compose.full.yml" ]]; then
                docker-compose -f infra/docker/docker-compose.full.yml up -d vortex-db 2>/dev/null || true
            fi
        fi
        
        # Aguardar Oracle estar pronto
        if docker ps --format "{{.Names}}" | grep -q "^vortex-db$"; then
            wait_for_oracle
            databases_started=true
        fi
    fi
    
    if [[ "$databases_started" == "true" ]]; then
        print_color $GREEN "✅ Bancos de dados iniciados com sucesso!"
        
        # Mostrar informações dos bancos
        if [[ "$ENVIRONMENT" == "prd" ]]; then
            if docker ps --format "{{.Names}}" | grep -q "^vortex-db$"; then
                print_color $GREEN "   🏛️  Oracle: localhost:1521 (ORCLCDB/ORCLPDB1)"
            fi
        fi
        
        if docker ps --format "{{.Names}}" | grep -q "^vortex-auth-db$"; then
            print_color $GREEN "   🐘 PostgreSQL: localhost:5432 (vortex_auth/vortex_auth_password)"
        fi
    else
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            print_color $GREEN "✅ Ambiente de desenvolvimento usa H2 (em memória)"
        else
            print_color $YELLOW "⚠️  Nenhum banco de dados foi iniciado"
        fi
    fi
}

# Função para aguardar o healthcheck do backend
wait_for_backend_healthcheck() {
    local host=$1
    local port=$2
    local max_attempts=60
    local attempt=1
    local url="http://$host:$port/health"
    
    print_color $BLUE "⏳ Aguardando healthcheck do backend em $url..."
    
    while [[ $attempt -le $max_attempts ]]; do
        if curl -s -o /dev/null -w "%{http_code}" "$url" | grep -q "200"; then
            print_color $GREEN "✅ Backend está saudável!"
            return 0
        fi
        
        print_color $YELLOW "⏳ Tentativa $attempt/$max_attempts - Aguardando backend..."
        sleep 2
        ((attempt++))
    done
    
    print_color $RED "❌ Timeout aguardando healthcheck do backend em $url"
    return 1
}

# Função para aguardar o healthcheck do serviço de autorização
wait_for_auth_healthcheck() {
    local host=$1
    local port=$2
    local max_attempts=60
    local attempt=1
    local url="http://$host:$port/q/health"
    
    print_color $BLUE "⏳ Aguardando healthcheck do serviço de autorização em $url..."
    
    while [[ $attempt -le $max_attempts ]]; do
        if curl -s -o /dev/null -w "%{http_code}" "$url" | grep -q "200"; then
            print_color $GREEN "✅ Serviço de autorização está saudável!"
            return 0
        fi
        
        print_color $YELLOW "⏳ Tentativa $attempt/$max_attempts - Aguardando serviço de autorização..."
        sleep 2
        ((attempt++))
    done
    
    print_color $RED "❌ Timeout aguardando healthcheck do serviço de autorização em $url"
    return 1
}

# Função para executar backend de autorização em desenvolvimento
start_auth_backend_dev() {
    print_color $BLUE "🔐 Iniciando Backend de Autorização em modo desenvolvimento..."
    
    cd backend/vortex-authorization-service
    
    # Verificar se Maven está disponível
    if command -v mvn &> /dev/null; then
        print_color $GREEN "📦 Executando com Maven local..."
        
        # Verificar se há processos na porta 8081 (conforme PORTS.md)
        if lsof -Pi :8081 -sTCP:LISTEN -t >/dev/null 2>&1; then
            print_color $YELLOW "⚠️  Porta 8081 ocupada (Auth Backend). Liberando conforme PORTS.md..."
            pkill -f "vortex-authorization-service" 2>/dev/null || true
            pkill -f "quarkus:dev" 2>/dev/null || true
            lsof -ti :8081 | xargs kill -9 2>/dev/null || true
            sleep 3
        fi
        
        # Criar diretório de logs se não existir
        mkdir -p ../../logs
        
        # Verificar se há erros de compilação primeiro
        print_color $BLUE "🔧 Verificando compilação..."
        if ! mvn compile -q; then
            print_color $RED "❌ Erro de compilação. Verifique o código fonte."
            cd ../..
            return 1
        fi
        
        # Definir variáveis de ambiente
        export QUARKUS_PROFILE=dev
        export QUARKUS_HTTP_PORT=8081
        export MAVEN_OPTS="-Xmx1g"
        
        # Configurações específicas para sistemas de mensageria
        if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
            export KAFKA_SERVERS=localhost:9092
            
            # Verificar se Kafka está rodando
            if ! docker ps | grep -q "vortex-kafka"; then
                print_color $YELLOW "⚠️  Kafka não está rodando. Serviço de autorização pode ter problemas de conectividade."
            fi
        fi
        
        print_color $BLUE "🚀 Iniciando serviço de autorização..."
        nohup mvn quarkus:dev -Dquarkus.http.port=8081 > ../../logs/auth-backend.log 2>&1 &
        AUTH_BACKEND_PID=$!
        echo $AUTH_BACKEND_PID > ../../logs/auth-backend.pid
        print_color $GREEN "✅ Backend de autorização iniciado (PID: $AUTH_BACKEND_PID)"
        
        # Aguardar healthcheck
        wait_for_auth_healthcheck "localhost" "8081"
    else
        print_color $YELLOW "📦 Maven não encontrado, usando Docker..."
        
        # Usar Docker para executar o backend de autorização
        docker build -t vortex-auth-service:dev -f src/main/docker/Dockerfile.jvm .
        docker run -d --name vortex-auth-service \
            -p 8081:8081 \
            -e QUARKUS_PROFILE=dev \
            -e KAFKA_SERVERS=localhost:9092 \
            vortex-auth-service:dev
        
        print_color $GREEN "✅ Backend de autorização iniciado no Docker"
        
        # Aguardar healthcheck
        wait_for_auth_healthcheck "localhost" "8081"
    fi
    
    cd ../..
}

# Função para executar frontend de autorização
start_auth_frontend() {
    print_color $BLUE "🔐 Iniciando Frontend de Autorização..."
    
    cd frontend/vortex-authorization-service
    
    # Verificar se node_modules existe
    if [[ ! -d "node_modules" ]]; then
        if [[ "$NPM_AVAILABLE" == "true" ]]; then
            print_color $YELLOW "📦 Instalando dependências do frontend de autorização..."
            npm install
        else
            print_color $RED "❌ npm não disponível e node_modules não existe."
            cd ../..
            return 1
        fi
    fi
    
    # Verificar se há processos na porta 3001 (conforme PORTS.md - Auth Frontend)
    if lsof -Pi :3001 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_color $YELLOW "⚠️  Porta 3001 ocupada (Auth Frontend). Liberando conforme PORTS.md..."
        pkill -f "vite.*3001" 2>/dev/null || true
        lsof -ti :3001 | xargs kill -9 2>/dev/null || true
        sleep 2
    fi
    
    if [[ "$ENVIRONMENT" == "dev" ]]; then
        if [[ "$NPM_AVAILABLE" == "true" ]]; then
            print_color $GREEN "🔥 Iniciando servidor de desenvolvimento Vite na porta 3001..."
            
            # Verificar se vite.config.ts já está configurado para porta 3001 (PORTS.md)
            if ! grep -q "port: 3001" vite.config.ts 2>/dev/null; then
                print_color $YELLOW "📝 Configurando porta 3001 conforme PORTS.md..."
                sed -i.bak 's/port: [0-9]*/port: 3001/g' vite.config.ts 2>/dev/null || true
            fi
            
            nohup npm run dev > ../../logs/auth-frontend.log 2>&1 &
            AUTH_FRONTEND_PID=$!
            echo $AUTH_FRONTEND_PID > ../../logs/auth-frontend.pid
            print_color $GREEN "✅ Frontend de autorização iniciado (PID: $AUTH_FRONTEND_PID)"
            
            # Aguardar servidor estar pronto
            sleep 5
            
            # Restaurar configuração original
            if [[ -f "vite.config.ts.bak" ]]; then
                mv vite.config.ts.bak vite.config.ts
            fi
        else
            print_color $RED "❌ npm não disponível para executar frontend em desenvolvimento."
            cd ../..
            return 1
        fi
    else
        print_color $GREEN "🏗️  Fazendo build do frontend de autorização para produção..."
        if [[ "$NPM_AVAILABLE" == "true" ]]; then
            npm run build
            print_color $GREEN "📦 Servindo frontend de autorização com preview na porta 3001..."
            
            # Modificar temporariamente para porta 3001
            nohup npm run preview -- --port 3001 > ../../logs/auth-frontend.log 2>&1 &
            AUTH_FRONTEND_PID=$!
            echo $AUTH_FRONTEND_PID > ../../logs/auth-frontend.pid
            print_color $GREEN "✅ Frontend de autorização preview iniciado (PID: $AUTH_FRONTEND_PID)"
        else
            print_color $RED "❌ npm não disponível para build do frontend."
            cd ../..
            return 1
        fi
    fi
    
    cd ../..
}

# Função para executar backend em desenvolvimento
start_backend_dev() {
    print_color $BLUE "🔧 Iniciando Backend em modo desenvolvimento..."
    
    cd backend/vortex-application-service
    
    # Definir perfis Spring baseado no sistema de mensageria
    SPRING_PROFILES="dev"
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        SPRING_PROFILES="dev,kafka"
    elif [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
        SPRING_PROFILES="dev,rabbitmq"
    elif [[ "$MESSAGING_SYSTEM" == "sqs" ]]; then
        SPRING_PROFILES="dev,sqs"

    fi
    
    # Verificar se Maven está disponível
    if command -v mvn &> /dev/null; then
        print_color $GREEN "📦 Executando com Maven local..."
        export SPRING_PROFILES_ACTIVE="$SPRING_PROFILES"
        
        # Configurações específicas para sistemas de mensageria
        if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
            export KAFKA_ENABLED=true
            export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
            
            # Verificar se Kafka está rodando antes de iniciar o backend
            if ! docker ps | grep -q "vortex-kafka"; then
                print_color $YELLOW "⚠️  Kafka não está rodando. Tentando iniciar..."
                if ! start_kafka; then
                    print_color $RED "❌ Falha ao iniciar Kafka. Backend pode ter problemas de conectividade."
                fi
            else
                # Kafka está rodando, mas vamos aguardar estar completamente pronto
                wait_for_kafka
            fi
        else
            export KAFKA_ENABLED=false
        fi
        
        # Configurações específicas para RabbitMQ
        if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
            export RABBITMQ_ENABLED=true
            export SPRING_RABBITMQ_HOST=localhost
            export SPRING_RABBITMQ_PORT=5672
            export SPRING_RABBITMQ_USERNAME=vortex
            export SPRING_RABBITMQ_PASSWORD=vortex123
        else
            export RABBITMQ_ENABLED=false
        fi
        
        nohup mvn spring-boot:run > ../../logs/backend.log 2>&1 &
        BACKEND_PID=$!
        echo $BACKEND_PID > ../../logs/backend.pid
        print_color $GREEN "✅ Backend iniciado (PID: $BACKEND_PID) com perfis: $SPRING_PROFILES"
        
        # Aguardar healthcheck do backend
        wait_for_backend_healthcheck "localhost" "8080"
    else
        print_color $YELLOW "📦 Maven não encontrado, usando Docker..."
        # Criar docker-compose temporário para dev
        NETWORK_CONFIG=""
        if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
            NETWORK_CONFIG="
    networks:
      - vortex-kafka-network

networks:
  vortex-kafka-network:
    external: true"
        fi
        
        cat > docker-compose.dev.yml << EOF
version: '3.8'
services:
  vortex-dev:
    build: .
    container_name: vortex-app-dev
    environment:
      SPRING_PROFILES_ACTIVE: $SPRING_PROFILES
      KAFKA_ENABLED: ${KAFKA_ENABLED:-false}
      SPRING_KAFKA_BOOTSTRAP_SERVERS: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
    ports:
      - "8080:8080"$NETWORK_CONFIG
EOF
        docker-compose -f docker-compose.dev.yml up -d
        print_color $GREEN "✅ Backend iniciado no Docker com perfis: $SPRING_PROFILES"
        
        # Aguardar healthcheck do backend no Docker
        wait_for_backend_healthcheck "localhost" "8080"
    fi
    
    cd ../..
}

# Função para executar backend em produção
start_backend_prd() {
    print_color $BLUE "🚀 Iniciando Backend em modo produção..."
    
    # Definir perfis Spring baseado no sistema de mensageria
    SPRING_PROFILES="prd"
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        SPRING_PROFILES="prd,kafka"
    elif [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
        SPRING_PROFILES="prd,rabbitmq"
    elif [[ "$MESSAGING_SYSTEM" == "sqs" ]]; then
        SPRING_PROFILES="prd,sqs"

    fi
    
    # Configurar variáveis de ambiente para Kafka se necessário
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        export KAFKA_ENABLED=true
        export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
        
        # Garantir que o Kafka está rodando antes de iniciar o backend
        if ! docker ps | grep -q "vortex-kafka"; then
            print_color $YELLOW "⚠️  Kafka não está rodando. Iniciando Kafka primeiro..."
            if ! start_kafka; then
                print_color $RED "❌ Falha ao iniciar Kafka. Backend não pode ser iniciado."
                return 1
            fi
        else
            # Kafka está rodando, mas vamos aguardar estar completamente pronto
            if ! wait_for_kafka; then
                print_color $YELLOW "⚠️  Kafka não está completamente pronto, mas continuando..."
            fi
        fi
    else
        export KAFKA_ENABLED=false
    fi
    
    if [[ "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
        # Se frontend também deve rodar e npm não está disponível, usar compose completo
        print_color $YELLOW "📦 Usando Docker Compose completo (Backend + Oracle + Frontend)..."
        
        # Criar arquivo de environment para o Docker Compose
        cat > .env << EOF
SPRING_PROFILES_ACTIVE=$SPRING_PROFILES
KAFKA_ENABLED=${KAFKA_ENABLED:-false}
SPRING_KAFKA_BOOTSTRAP_SERVERS=${SPRING_KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
EOF
        
        # Escolher arquivo de compose baseado no sistema de mensageria
        if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
            print_color $BLUE "🚀 Usando stack completa com Kafka integrado..."
            
            # Verificar se o arquivo existe
            if [[ ! -f "infra/docker/docker-compose.full-kafka.yml" ]]; then
                print_color $RED "❌ Arquivo infra/docker/docker-compose.full-kafka.yml não encontrado!"
                print_color $YELLOW "💡 Usando configuração padrão e Kafka separado..."
                docker-compose -f infra/docker/docker-compose.full.yml up -d --build
            else
                docker-compose -f infra/docker/docker-compose.full-kafka.yml up -d --build
            fi
        else
            docker-compose -f infra/docker/docker-compose.full.yml up -d --build
        fi
        
        print_color $GREEN "✅ Stack completa iniciada com perfis: $SPRING_PROFILES"
        
        # Aguardar healthcheck do backend no Docker
        wait_for_backend_healthcheck "localhost" "8080"
    else
        # Apenas backend + Oracle
        cd infra/docker
        
        # Configurar variáveis de ambiente para Docker Compose
        export SPRING_PROFILES_ACTIVE="$SPRING_PROFILES"
        
        docker-compose up -d --build
        print_color $GREEN "✅ Backend e Oracle iniciados com perfis: $SPRING_PROFILES"
        
        # Aguardar healthcheck do backend no Docker
        wait_for_backend_healthcheck "localhost" "8080"
        cd ../..
    fi
}

# Função para gerar arquivo de configuração do tema
generate_theme_config() {
    local main_frontend_dir="frontend/vortex-application-service"
    local auth_frontend_dir="frontend/vortex-authorization-service"
    
    print_color $BLUE "🎨 Configurando tema ${COLOR_THEME}..."
    
    # Criar arquivo .env.local para frontend principal (Vue.js)
    cat > "${main_frontend_dir}/.env.local" << EOF
# Arquivo gerado automaticamente pelo start-vortex.sh
# Configuração do tema de cores selecionado pelo usuário

VITE_THEME_COLOR=${COLOR_THEME}
EOF
    
    # Criar arquivo .env.local para frontend de autorização (React)
    cat > "${auth_frontend_dir}/.env.local" << EOF
# Arquivo gerado automaticamente pelo start-vortex.sh
# Configuração do tema de cores selecionado pelo usuário

VITE_THEME_COLOR=${COLOR_THEME}
EOF
    
    print_color $GREEN "✅ Tema ${COLOR_THEME} configurado em ambos frontends:"
    print_color $GREEN "   - ${main_frontend_dir}/.env.local"
    print_color $GREEN "   - ${auth_frontend_dir}/.env.local"
}

# Função para executar frontend
start_frontend() {
    print_color $BLUE "🎨 Iniciando Frontend..."
    
    # Se já foi iniciado via Docker Compose completo, pular
    if [[ "$ENVIRONMENT" == "prd" && "$NPM_AVAILABLE" == "false" ]]; then
        print_color $GREEN "✅ Frontend já iniciado via Docker Compose"
        return 0
    fi
    
    # Gerar configuração do tema antes de iniciar
    generate_theme_config
    
    cd frontend/vortex-application-service
    
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
    
    # Verificar se há processos na porta 5173 (conforme PORTS.md - Main Frontend Dev)
    if lsof -Pi :5173 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_color $YELLOW "⚠️  Porta 5173 ocupada (Main Frontend). Liberando conforme PORTS.md..."
        pkill -f "vite.*5173" 2>/dev/null || true
        lsof -ti :5173 | xargs kill -9 2>/dev/null || true
        sleep 2
    fi

    if [[ "$ENVIRONMENT" == "dev" ]]; then
        if [[ "$NPM_AVAILABLE" == "true" ]]; then
            print_color $GREEN "🔥 Iniciando servidor de desenvolvimento Vite na porta 5173..."
            
            # Verificar se vite.config.ts já está configurado para porta 5173 (PORTS.md)
            if ! grep -q "port: 5173" vite.config.ts 2>/dev/null; then
                print_color $YELLOW "📝 Configurando porta 5173 conforme PORTS.md..."
                sed -i.bak 's/port: [0-9]*/port: 5173/g' vite.config.ts 2>/dev/null || true
            fi
            
            nohup npm run dev > ../../logs/frontend.log 2>&1 &
            FRONTEND_PID=$!
            echo $FRONTEND_PID > ../../logs/frontend.pid
            print_color $GREEN "✅ Frontend dev server iniciado (PID: $FRONTEND_PID)"
        else
            print_color $RED "❌ npm não disponível para executar frontend em desenvolvimento."
            cd ../..
            return 1
        fi
    else
        print_color $GREEN "🏗️  Fazendo build do frontend para produção..."
        if [[ "$NPM_AVAILABLE" == "true" ]]; then
            npm run build
            print_color $GREEN "📦 Servindo frontend com preview..."
            nohup npm run preview > ../../logs/frontend.log 2>&1 &
            FRONTEND_PID=$!
            echo $FRONTEND_PID > ../../logs/frontend.pid
            print_color $GREEN "✅ Frontend preview iniciado (PID: $FRONTEND_PID)"
        else
            print_color $RED "❌ npm não disponível para build do frontend."
            cd ../..
            return 1
        fi
    fi
    
    cd ../..
}

# Função para mostrar status
show_status() {
    print_color $CYAN "
📊 STATUS DOS SERVIÇOS
═══════════════════════════════════════════════════════════════
"
    
    # Status do serviço de autorização
    print_color $BLUE "🔐 SERVIÇO DE AUTORIZAÇÃO:"
    
    # Backend de autorização
    if wait_for_auth_healthcheck "localhost" "8081" >/dev/null 2>&1; then
        print_color $GREEN "   ✅ Backend de autorização rodando"
        print_color $GREEN "   🌐 API: http://localhost:8081"
        print_color $GREEN "   📚 Swagger: http://localhost:8081/q/swagger-ui"
        print_color $GREEN "   💚 Health: http://localhost:8081/q/health"
    else
        print_color $RED "   ❌ Backend de autorização não está rodando"
    fi
    
    # Frontend de autorização
    if [[ -f "logs/auth-frontend.pid" ]]; then
        PID=$(cat logs/auth-frontend.pid)
        if ps -p $PID > /dev/null 2>&1; then
            print_color $GREEN "   ✅ Frontend de autorização rodando (PID: $PID)"
            print_color $GREEN "   🌐 App: http://localhost:3001"
        else
            print_color $RED "   ❌ Frontend de autorização não está rodando"
        fi
    else
        print_color $RED "   ❌ Frontend de autorização não foi iniciado"
    fi
    
    # Status do sistema de mensageria
    if [[ "$MESSAGING_SYSTEM" != "none" ]]; then
        print_color $BLUE "📨 SISTEMA DE MENSAGERIA ($MESSAGING_SYSTEM):"
        
        if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
            # Verificar configuração simplificada primeiro
            if docker ps | grep -q "vortex-kafka-simple"; then
                print_color $GREEN "   ✅ Kafka rodando (configuração simplificada)"
                print_color $GREEN "   📡 Broker: localhost:9092"
                print_color $GREEN "   🌐 Kafka UI: http://localhost:8090"
                if docker ps | grep -q "vortex-zookeeper-simple"; then
                    print_color $GREEN "   🔗 Zookeeper: localhost:2181"
                fi
                
                # Verificar saúde do Kafka
                if docker ps --filter "name=vortex-kafka-simple" --filter "health=healthy" | grep -q "vortex-kafka-simple"; then
                    print_color $GREEN "   💚 Status: Saudável"
                else
                    print_color $YELLOW "   ⚠️  Status: Inicializando..."
                fi
                
                # Mostrar tópicos disponíveis
                local topics=$(docker exec vortex-kafka-simple kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | wc -l)
                if [[ $topics -gt 0 ]]; then
                    print_color $GREEN "   📋 Tópicos: $topics disponíveis"
                fi
                
            elif docker ps | grep -q "vortex-kafka"; then
                print_color $GREEN "   ✅ Kafka rodando (configuração legada)"
                print_color $GREEN "   📡 Broker: localhost:9092"
                print_color $GREEN "   🌐 Kafka UI: http://localhost:8090"
                if docker ps | grep -q "vortex-zookeeper"; then
                    print_color $GREEN "   🔗 Zookeeper: localhost:2181"
                fi
            else
                print_color $RED "   ❌ Kafka não está rodando"
                print_color $YELLOW "   💡 Execute: ./fix-kafka-issues.sh"
            fi
        fi
        
        if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
            if docker ps | grep -q "vortex-rabbitmq"; then
                print_color $GREEN "   ✅ RabbitMQ rodando"
                print_color $GREEN "   📡 AMQP: localhost:5672"
                print_color $GREEN "   🌐 Management UI: http://localhost:15672"
                print_color $GREEN "   👤 Usuário: vortex / Senha: vortex123"
                
                # Verificar saúde do RabbitMQ
                if docker exec vortex-rabbitmq rabbitmq-diagnostics status >/dev/null 2>&1; then
                    print_color $GREEN "   💚 Status: Saudável"
                else
                    print_color $YELLOW "   ⚠️  Status: Inicializando..."
                fi
            else
                print_color $RED "   ❌ RabbitMQ não está rodando"
                print_color $YELLOW "   💡 Execute: ./start-vortex.sh -m rabbitmq"
            fi
        fi
        
        if [[ "$MESSAGING_SYSTEM" == "sqs" ]]; then
            print_color $YELLOW "   ☁️  SQS: Configuração AWS necessária"
        fi
    fi
    
    # Status do backend principal
    if [[ "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "🔧 BACKEND PRINCIPAL ($ENVIRONMENT):"
        if wait_for_backend_healthcheck "localhost" "8080" >/dev/null 2>&1; then
            print_color $GREEN "   ✅ Rodando"
            print_color $GREEN "   🌐 API: http://localhost:8080"
            print_color $GREEN "   📚 Swagger: http://localhost:8080/swagger-ui.html"
            if [[ "$ENVIRONMENT" == "dev" ]]; then
                print_color $GREEN "   🗄️  H2 Console: http://localhost:8080/h2-console"
            else
                print_color $GREEN "   🗄️  Oracle: localhost:1521 (ORCLCDB/ORCLPDB1)"
            fi
        else
            print_color $RED "   ❌ Não está rodando ou não responde ao healthcheck"
        fi
    fi
    
    # Status do frontend principal
    if [[ "$RUN_FRONTEND" == "true" ]]; then
        print_color $BLUE "🎨 FRONTEND PRINCIPAL ($ENVIRONMENT):"
        
        # Verificar se está rodando via Docker
        if docker ps | grep -q "vortex-frontend"; then
            print_color $GREEN "   ✅ Rodando no Docker"
            print_color $GREEN "   🌐 App: http://localhost:3000"
        elif [[ -f "logs/frontend.pid" ]]; then
            PID=$(cat logs/frontend.pid)
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
    print_color $YELLOW "   ./start-vortex.sh --stop     # Parar todos os serviços"
    print_color $YELLOW "   ./start-vortex.sh --clean    # Limpar ambiente"
    print_color $YELLOW "   docker logs vortex-app -f    # Logs do backend (prd)"
    print_color $YELLOW "   docker logs vortex-db -f     # Logs do Oracle"
    print_color $YELLOW "   tail -f logs/backend.log          # Logs do backend (dev)"
    print_color $YELLOW "   tail -f logs/frontend.log         # Logs do frontend"
    print_color $YELLOW "   tail -f logs/auth-backend.log     # Logs do backend de autorização"
    print_color $YELLOW "   tail -f logs/auth-frontend.log    # Logs do frontend de autorização"
    
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        print_color $CYAN "
📨 COMANDOS KAFKA:
═══════════════════════════════════════════════════════════════"
        
        # Verificar qual configuração está rodando
        if docker ps | grep -q "vortex-kafka-simple"; then
            print_color $YELLOW "   # Configuração Simplificada (Recomendada):"
            print_color $YELLOW "   docker logs vortex-kafka-simple -f                           # Logs do Kafka"
            print_color $YELLOW "   docker exec vortex-kafka-simple kafka-topics --bootstrap-server localhost:9092 --list   # Listar tópicos"
            print_color $YELLOW "   docker exec vortex-kafka-simple kafka-consumer-groups --bootstrap-server localhost:9092 --list # Consumer groups"
            print_color $YELLOW "   ./infra/kafka/fix-kafka-issues.sh                           # Correção automática"
        else
            print_color $YELLOW "   # Configuração Legada:"
            print_color $YELLOW "   docker logs vortex-kafka -f                                  # Logs do Kafka"
            print_color $YELLOW "   docker exec vortex-kafka kafka-topics --list                # Listar tópicos"
            print_color $YELLOW "   docker exec vortex-kafka kafka-consumer-groups --list       # Consumer groups"
        fi
        
        print_color $YELLOW "   # Comandos Gerais:"
        print_color $YELLOW "   ./infra/kafka/fix-kafka-issues.sh                           # Correção automática de problemas"
    fi
    
    if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
        print_color $CYAN "
📨 COMANDOS RABBITMQ:
════════════════════════════════════════════════════════════════"
        print_color $YELLOW "   docker logs vortex-rabbitmq -f                              # Logs do RabbitMQ"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_queues         # Listar filas"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_exchanges      # Listar exchanges"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmqctl list_bindings       # Listar bindings"
        print_color $YELLOW "   docker exec vortex-rabbitmq rabbitmq-diagnostics status     # Status do RabbitMQ"
    fi
}

# Função para mostrar logs
show_logs() {
    if [[ "$ENVIRONMENT" == "prd" && "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "📄 Mostrando logs do backend (produção)..."
        sleep 2
        docker logs vortex-app -f
    elif [[ "$ENVIRONMENT" == "dev" && "$RUN_BACKEND" == "true" ]]; then
        print_color $BLUE "📄 Mostrando logs do backend (desenvolvimento)..."
        sleep 2
        if [[ -f "logs/backend.log" ]]; then
            tail -f logs/backend.log
        else
            docker logs vortex-app-dev -f 2>/dev/null || echo "Logs não disponíveis"
        fi
    fi
}

# Função principal
main() {
    show_banner
    mkdir -p logs
    
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
            -c|--components)
                COMPONENTS="$2"
                shift 2
                ;;
            -m|--messaging)
                MESSAGING_SYSTEM="$2"
                shift 2
                ;;
            --no-interaction)
                NO_INTERACTION="true"
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
            --fix-kafka)
                fix_kafka_issues
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
    
    # Validar sistema de mensageria se fornecido
    if [[ -n "$MESSAGING_SYSTEM" && "$MESSAGING_SYSTEM" != "kafka" && "$MESSAGING_SYSTEM" != "rabbitmq" && "$MESSAGING_SYSTEM" != "sqs" && "$MESSAGING_SYSTEM" != "none" ]]; then
        print_color $RED "❌ Sistema de mensageria inválido: $MESSAGING_SYSTEM. Use 'kafka', 'rabbitmq', 'sqs' ou 'none'."
        exit 1
    fi
    
    # Validar componentes se fornecido
    if [[ -n "$COMPONENTS" && "$COMPONENTS" != "full" && "$COMPONENTS" != "backend" && "$COMPONENTS" != "frontend" ]]; then
        print_color $RED "❌ Componentes inválidos: $COMPONENTS. Use 'full', 'backend' ou 'frontend'."
        exit 1
    fi
    
    # Verificar pré-requisitos
    check_prerequisites
    
    # Escolher cor do tema se não fornecido (PRIMEIRA pergunta)
    choose_color_theme
    
    # Escolher ambiente se não fornecido
    choose_environment
    
    # Escolher sistema de mensageria se não fornecido
    choose_messaging
    
    # Escolher componentes
    choose_components
    
    # Parar serviços existentes
    print_color $YELLOW "🔄 Verificando serviços existentes..."
    stop_services
    
    # Iniciar serviços
    print_color $BLUE "🚀 Iniciando serviços..."
    
    # PRIMEIRO: Iniciar bancos de dados (Oracle e PostgreSQL)
    start_databases
    
    # SEGUNDO: Iniciar Kafka se necessário (apenas se não for integrado com stack completa)
    if [[ "$MESSAGING_SYSTEM" == "kafka" ]]; then
        if [[ "$ENVIRONMENT" == "prd" && "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
            print_color $BLUE "📦 Kafka será iniciado integrado com a stack completa..."
        else
            start_kafka
        fi
    fi
    
    # TERCEIRO: Iniciar RabbitMQ se necessário (apenas se não for integrado com stack completa)
    if [[ "$MESSAGING_SYSTEM" == "rabbitmq" ]]; then
        if [[ "$ENVIRONMENT" == "prd" && "$RUN_FRONTEND" == "true" && "$NPM_AVAILABLE" == "false" ]]; then
            print_color $BLUE "📦 RabbitMQ será iniciado integrado com a stack completa..."
        else
            start_rabbitmq
        fi
    fi
    
    # QUARTO: Iniciar serviços de autorização
    print_color $BLUE "🔐 Iniciando serviços de autorização..."
    start_auth_backend_dev
    start_auth_frontend
    
    if [[ "$RUN_BACKEND" == "true" ]]; then
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            start_backend_dev
        else
            start_backend_prd
        fi
    fi
    
    if [[ "$RUN_FRONTEND" == "true" ]]; then
        start_frontend
    fi
    
    # Mostrar status
    show_status
    
    # Mostrar logs se solicitado
    if [[ "$SHOW_LOGS" == "true" ]]; then
        show_logs
    fi
    
    print_color $GREEN "
🎉 Vortex iniciado com sucesso!
═══════════════════════════════════════════════════════════════

🔐 SERVIÇOS DE AUTORIZAÇÃO:
   - Backend: http://localhost:8081
   - Frontend: http://localhost:3001
   - Swagger: http://localhost:8081/q/swagger-ui

🚀 SERVIÇOS PRINCIPAIS:
   - Backend: http://localhost:8080
   - Frontend: http://localhost:5173 (dev) ou http://localhost:4173 (prd)

Para parar os serviços, execute: ./start-vortex.sh --stop
"
}

# Variáveis globais
ENVIRONMENT=""
MESSAGING_SYSTEM=""
COMPONENTS=""
BACKEND_ONLY="false"
FRONTEND_ONLY="false"
RUN_BACKEND="true"
RUN_FRONTEND="true"
SHOW_LOGS="false"
NO_INTERACTION="false"
NODE_AVAILABLE="false"
NPM_AVAILABLE="false"

# Executar função principal
main "$@" 