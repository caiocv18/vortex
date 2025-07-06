#!/bin/bash

# ================================
# SCRIPT PARA RESOLVER PROBLEMAS DO KAFKA
# ================================

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

print_header "🔧 VORTEX - CORREÇÃO DE PROBLEMAS DO KAFKA"

# ================================
# 1. LIMPEZA DE CONTAINERS E VOLUMES
# ================================

print_header "🧹 LIMPEZA DE CONTAINERS E VOLUMES ANTIGOS"

print_color $YELLOW "Parando containers relacionados ao Kafka..."
docker stop vortex-kafka vortex-zookeeper vortex-kafka-ui 2>/dev/null || true
docker stop vortex-kafka-simple vortex-zookeeper-simple vortex-kafka-ui-simple 2>/dev/null || true

print_color $YELLOW "Removendo containers antigos..."
docker rm vortex-kafka vortex-zookeeper vortex-kafka-ui 2>/dev/null || true
docker rm vortex-kafka-simple vortex-zookeeper-simple vortex-kafka-ui-simple 2>/dev/null || true

print_color $YELLOW "Removendo volumes antigos..."
docker volume rm vortex_kafka-data vortex_zookeeper-data vortex_zookeeper-logs 2>/dev/null || true
docker volume rm vortex_kafka-simple-data vortex-kafka-simple_kafka-simple-data 2>/dev/null || true

print_color $YELLOW "Limpando dados corrompidos do Kafka (Cluster ID conflicts)..."
docker volume prune -f 2>/dev/null || true

print_color $YELLOW "Removendo redes antigas..."
docker network rm vortex-network vortex-kafka-network vortex-simple 2>/dev/null || true

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

check_port 2181 "ZooKeeper" || PORTS_OK=false
check_port 9092 "Kafka" || PORTS_OK=false
check_port 8090 "Kafka UI" || PORTS_OK=false

if [ "$PORTS_OK" = false ]; then
    print_color $RED "❌ Algumas portas estão em uso. Por favor, pare os processos antes de continuar."
    exit 1
fi

# ================================
# 3. INICIALIZAÇÃO DO KAFKA SIMPLES
# ================================

print_header "🚀 INICIANDO KAFKA SIMPLES"

print_color $YELLOW "Iniciando Kafka com configuração simplificada..."
docker-compose -f docker-compose.kafka-simple.yml up -d

print_color $YELLOW "Aguardando inicialização do Kafka..."

# Aguardar com verificação inteligente
max_attempts=60
attempt=1
kafka_healthy=false

while [[ $attempt -le $max_attempts ]]; do
    if docker ps --filter "name=vortex-kafka-simple" --filter "health=healthy" | grep -q "vortex-kafka-simple"; then
        kafka_healthy=true
        print_color $GREEN "✅ Kafka inicializado com sucesso (tentativa $attempt/$max_attempts)"
        break
    fi
    
    # Verificar se há erro de Cluster ID
    if docker logs vortex-kafka-simple 2>&1 | grep -q "InconsistentClusterIdException"; then
        print_color $YELLOW "⚠️  Detectado conflito de Cluster ID. Limpando dados..."
        
        # Parar containers
        docker-compose -f docker-compose.kafka-simple.yml down -v 2>/dev/null || true
        
        # Limpar volumes específicos
        docker volume rm vortex_kafka-simple-data 2>/dev/null || true
        docker system prune -f 2>/dev/null || true
        
        # Reiniciar
        print_color $YELLOW "🔄 Reiniciando Kafka após limpeza..."
        docker-compose -f docker-compose.kafka-simple.yml up -d
        
        # Resetar contador para dar mais tempo após limpeza
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
    ((attempt++))
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
# 4. VERIFICAÇÃO DE SAÚDE
# ================================

print_header "🏥 VERIFICAÇÃO DE SAÚDE DOS SERVIÇOS"

check_service() {
    local container=$1
    local service_name=$2
    
    if docker ps | grep -q $container; then
        if docker exec $container echo "test" >/dev/null 2>&1; then
            print_color $GREEN "✅ $service_name está rodando e responsivo"
            return 0
        else
            print_color $RED "❌ $service_name está rodando mas não responsivo"
            return 1
        fi
    else
        print_color $RED "❌ $service_name não está rodando"
        return 1
    fi
}

SERVICES_OK=true

check_service "vortex-zookeeper-simple" "ZooKeeper" || SERVICES_OK=false
check_service "vortex-kafka-simple" "Kafka" || SERVICES_OK=false
check_service "vortex-kafka-ui-simple" "Kafka UI" || SERVICES_OK=false

if [ "$SERVICES_OK" = false ]; then
    print_color $RED "❌ Alguns serviços não estão funcionando corretamente."
    print_color $YELLOW "Verificando logs..."
    
    echo
    print_color $YELLOW "=== LOGS DO ZOOKEEPER ==="
    docker logs vortex-zookeeper-simple --tail 20
    
    echo
    print_color $YELLOW "=== LOGS DO KAFKA ==="
    docker logs vortex-kafka-simple --tail 20
    
    exit 1
fi

# ================================
# 5. TESTE DE CONECTIVIDADE
# ================================

print_header "🔌 TESTE DE CONECTIVIDADE"

print_color $YELLOW "Testando conectividade com Kafka..."

# Criar um tópico de teste
if docker exec vortex-kafka-simple kafka-topics --create --topic test-topic --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1 2>/dev/null; then
    print_color $GREEN "✅ Tópico de teste criado com sucesso"
else
    print_color $RED "❌ Falha ao criar tópico de teste"
    exit 1
fi

# Listar tópicos
if docker exec vortex-kafka-simple kafka-topics --list --bootstrap-server localhost:9092 | grep -q "test-topic"; then
    print_color $GREEN "✅ Tópico de teste listado com sucesso"
else
    print_color $RED "❌ Falha ao listar tópicos"
    exit 1
fi

# Deletar tópico de teste
docker exec vortex-kafka-simple kafka-topics --delete --topic test-topic --bootstrap-server localhost:9092 >/dev/null 2>&1

# ================================
# 6. CONFIGURAÇÃO PARA APLICAÇÃO
# ================================

print_header "⚙️ CONFIGURAÇÃO PARA APLICAÇÃO"

print_color $YELLOW "Criando arquivo de configuração para a aplicação..."

cat > application-kafka-test.properties << EOF
# ================================
# CONFIGURAÇÃO KAFKA PARA TESTE
# ================================

# Configurações básicas do Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=vortex-inventory-group

# Habilitar Kafka
kafka.enabled=true

# Configurações de fallback
kafka.fallback.enabled=true
kafka.connection.timeout=5000
kafka.retry.attempts=3
kafka.retry.delay=2000

# Logs
logging.level.br.com.vortex.desafio.backend.service.KafkaProducerService=DEBUG
logging.level.br.com.vortex.desafio.backend.service.KafkaConsumerService=DEBUG
logging.level.org.springframework.kafka=INFO
logging.level.org.apache.kafka=WARN
EOF

print_color $GREEN "✅ Arquivo application-kafka-test.properties criado"

# ================================
# 7. INSTRUÇÕES FINAIS
# ================================

print_header "📋 INSTRUÇÕES FINAIS"

print_color $GREEN "🎉 Kafka configurado com sucesso!"
echo
print_color $BLUE "📊 Acesse o Kafka UI em: http://localhost:8090"
print_color $BLUE "🔗 Kafka Bootstrap Server: localhost:9092"
echo
print_color $YELLOW "Para testar a aplicação:"
    print_color $YELLOW "1. Copie o arquivo application-kafka-test.properties para backend/vortex-application-service/src/main/resources/"
print_color $YELLOW "2. Execute a aplicação com o perfil: --spring.profiles.active=kafka"
print_color $YELLOW "3. Teste adicionando estoque em um produto"
echo
print_color $BLUE "Para parar o Kafka:"
print_color $BLUE "docker-compose -f docker-compose.kafka-simple.yml down -v"
echo
print_color $GREEN "✅ Configuração concluída com sucesso!" 