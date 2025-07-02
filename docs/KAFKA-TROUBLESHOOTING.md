# 🔧 Guia de Resolução de Problemas do Kafka - NEXDOM

## 📋 **Problemas Identificados e Soluções**

### **1. 🚨 Problema Principal: Kafka não está executando**

**Sintomas:**
- Erro: `Couldn't resolve server kafka:29092 from bootstrap.servers as DNS resolution failed for kafka`
- Erro: `No resolvable bootstrap urls given in bootstrap.servers`
- Aplicação falha ao tentar adicionar estoque

**Causa Raiz:**
- Containers do Kafka não estão rodando
- Configuração incorreta de rede
- Conflitos de porta

---

## 🛠️ **Soluções Implementadas**

### **Solução 1: Script Automático de Correção**

**Uso Rápido:**
```bash
# Dar permissão de execução
chmod +x fix-kafka-issues.sh

# Executar o script
./fix-kafka-issues.sh
```

**O que o script faz:**
- ✅ Limpa containers e volumes antigos
- ✅ Verifica portas em uso
- ✅ Inicia Kafka com configuração simplificada
- ✅ Testa conectividade
- ✅ Cria configuração para aplicação

---

### **Solução 2: Configuração Manual**

#### **Passo 1: Limpeza**
```bash
# Parar containers antigos
docker stop nexdom-kafka nexdom-zookeeper nexdom-kafka-ui 2>/dev/null || true

# Remover containers
docker rm nexdom-kafka nexdom-zookeeper nexdom-kafka-ui 2>/dev/null || true

# Remover volumes
docker volume rm nexdom_kafka-data nexdom_zookeeper-data nexdom_zookeeper-logs 2>/dev/null || true

# Remover redes
docker network rm nexdom-network nexdom-kafka-network 2>/dev/null || true
```

#### **Passo 2: Iniciar Kafka Simples**
```bash
# Usar o docker-compose simplificado
docker-compose -f docker-compose.kafka-simple.yml up -d

# Aguardar inicialização
sleep 30

# Verificar status
docker-compose -f docker-compose.kafka-simple.yml ps
```

#### **Passo 3: Configurar Aplicação**
```bash
# Copiar configuração de teste
cp application-kafka-test.properties backend/src/main/resources/

# Executar aplicação com perfil Kafka
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=kafka
```

---

## 🔍 **Diagnóstico de Problemas**

### **Verificar Status dos Serviços**
```bash
# Verificar containers
docker ps | grep nexdom

# Verificar logs do Kafka
docker logs nexdom-kafka-simple --tail 50

# Verificar logs do ZooKeeper
docker logs nexdom-zookeeper-simple --tail 50

# Verificar saúde do Kafka
docker exec nexdom-kafka-simple kafka-broker-api-versions --bootstrap-server localhost:9092
```

### **Verificar Conectividade**
```bash
# Listar tópicos
docker exec nexdom-kafka-simple kafka-topics --list --bootstrap-server localhost:9092

# Criar tópico de teste
docker exec nexdom-kafka-simple kafka-topics --create --topic test --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1

# Produzir mensagem de teste
echo "test message" | docker exec -i nexdom-kafka-simple kafka-console-producer --bootstrap-server localhost:9092 --topic test

# Consumir mensagem de teste
docker exec nexdom-kafka-simple kafka-console-consumer --bootstrap-server localhost:9092 --topic test --from-beginning --max-messages 1
```

### **Verificar Portas**
```bash
# Verificar se as portas estão livres
lsof -i :2181  # ZooKeeper
lsof -i :9092  # Kafka
lsof -i :8090  # Kafka UI
```

---

## ⚙️ **Configurações Importantes**

### **application-kafka.properties**
```properties
# Configuração flexível para bootstrap servers
spring.kafka.bootstrap-servers=${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

# Habilitar/desabilitar Kafka
kafka.enabled=${KAFKA_ENABLED:true}

# Configurações de fallback
kafka.fallback.enabled=true
kafka.connection.timeout=5000
kafka.retry.attempts=3
kafka.retry.delay=2000
```

### **docker-compose.kafka-simple.yml**
- Configuração simplificada para desenvolvimento
- Sem dependências externas complexas
- Health checks configurados
- Limpeza automática de logs

---

## 🚀 **Como Testar a Integração**

### **1. Iniciar Kafka**
```bash
./fix-kafka-issues.sh
```

### **2. Verificar Kafka UI**
- Acesse: http://localhost:8090
- Verifique se o cluster `nexdom-simple` aparece
- Confirme que não há erros de conexão

### **3. Testar Aplicação**
```bash
# Executar aplicação
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=kafka

# Em outro terminal, testar endpoint
curl -X POST http://localhost:8080/api/movimentos-estoque \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId": 1,
    "tipoMovimentacao": "ENTRADA",
    "quantidadeMovimentada": 10
  }'
```

### **4. Verificar Eventos no Kafka**
- Acesse Kafka UI: http://localhost:8090
- Vá para Topics → `nexdom.movimento.estoque`
- Verifique se as mensagens estão sendo produzidas

---

## 🔧 **Problemas Específicos e Soluções**

### **Problema: "node already exists and owner does not match current session"**
**Solução:**
```bash
# Limpar dados do ZooKeeper
docker-compose -f docker-compose.kafka-simple.yml down -v
docker volume rm nexdom-kafka-simple_kafka-simple-data 2>/dev/null || true
docker-compose -f docker-compose.kafka-simple.yml up -d
```

### **Problema: "DNS resolution failed for kafka"**
**Solução:**
- Usar `localhost:9092` em vez de `kafka:29092` quando executar fora do Docker
- Configurar variável de ambiente: `SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092`

### **Problema: Aplicação não conecta no Kafka**
**Solução:**
```bash
# Verificar se Kafka está rodando
docker ps | grep kafka

# Verificar logs da aplicação
tail -f backend/logs/application.log | grep -i kafka

# Testar conectividade manual
telnet localhost 9092
```

### **Problema: Tópicos não são criados automaticamente**
**Solução:**
```bash
# Criar tópicos manualmente
docker exec nexdom-kafka-simple kafka-topics --create --topic nexdom.movimento.estoque --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
docker exec nexdom-kafka-simple kafka-topics --create --topic nexdom.produto.events --bootstrap-server localhost:9092 --partitions 2 --replication-factor 1
docker exec nexdom-kafka-simple kafka-topics --create --topic nexdom.alertas.estoque --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
docker exec nexdom-kafka-simple kafka-topics --create --topic nexdom.auditoria --bootstrap-server localhost:9092 --partitions 2 --replication-factor 1
```

---

## 📊 **Monitoramento e Logs**

### **Logs Importantes**
```bash
# Logs da aplicação Spring Boot
tail -f backend/logs/application.log | grep -E "(KAFKA|ERROR|WARN)"

# Logs do Kafka
docker logs nexdom-kafka-simple -f | grep -E "(ERROR|WARN)"

# Logs do ZooKeeper
docker logs nexdom-zookeeper-simple -f | grep -E "(ERROR|WARN)"
```

### **Métricas no Kafka UI**
- **Brokers**: Deve mostrar 1 broker ativo
- **Topics**: Devem aparecer os tópicos do NEXDOM
- **Consumers**: Devem mostrar os grupos de consumidores ativos
- **Messages**: Verificar se mensagens estão sendo produzidas/consumidas

---

## 🏆 **Configuração de Produção**

### **Para Ambiente de Produção:**
1. Use o `docker-compose.full-kafka.yml`
2. Configure replicação adequada
3. Configure autenticação e autorização
4. Configure monitoramento com Prometheus/Grafana
5. Configure backup automático

### **Variáveis de Ambiente para Produção:**
```bash
export KAFKA_ENABLED=true
export SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
export SPRING_PROFILES_ACTIVE=prd,kafka
```

---

## 📞 **Suporte e Contato**

Se os problemas persistirem:

1. **Execute o script de diagnóstico:**
   ```bash
   ./fix-kafka-issues.sh
   ```

2. **Colete logs:**
   ```bash
   docker logs nexdom-kafka-simple > kafka.log 2>&1
   docker logs nexdom-zookeeper-simple > zookeeper.log 2>&1
   ```

3. **Verifique a documentação oficial:**
   - [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
   - [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)

---

## ✅ **Checklist de Verificação**

- [ ] Kafka está rodando (`docker ps | grep kafka`)
- [ ] ZooKeeper está rodando (`docker ps | grep zookeeper`)
- [ ] Portas 2181, 9092, 8090 estão livres
- [ ] Kafka UI acessível em http://localhost:8090
- [ ] Aplicação conecta no Kafka (logs sem erros)
- [ ] Tópicos são criados automaticamente
- [ ] Mensagens são produzidas nos tópicos
- [ ] Consumidores estão processando mensagens

**🎉 Se todos os itens estão marcados, o Kafka está funcionando corretamente!** 