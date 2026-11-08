# 🔧 Guia de Resolução de Problemas do Kafka - VORTEX

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
docker stop vortex-kafka vortex-zookeeper vortex-kafka-ui 2>/dev/null || true

# Remover containers
docker rm vortex-kafka vortex-zookeeper vortex-kafka-ui 2>/dev/null || true

# Remover volumes
docker volume rm vortex_kafka-data vortex_zookeeper-data vortex_zookeeper-logs 2>/dev/null || true

# Remover redes
docker network rm vortex-network vortex-kafka-network 2>/dev/null || true
```

#### **Passo 2: Iniciar Kafka Simples**
```bash
# Usar o compose simplificado (Docker Compose v2; a rede vortex-simple é external)
docker network inspect vortex-simple > /dev/null 2>&1 || docker network create vortex-simple
docker compose -f infra/docker/docker-compose.kafka-simple.yml up -d kafka

# Verificar status (o healthcheck fica healthy quando o listener abre)
docker compose -f infra/docker/docker-compose.kafka-simple.yml ps
```

#### **Passo 3: Configurar Aplicação**
```bash
# Copiar configuração de teste
cp application-kafka-test.properties backend/vortex-application-service/src/main/resources/

# Executar aplicação com perfil Kafka
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=kafka
```

---

## 🔍 **Diagnóstico de Problemas**

### **Verificar Status dos Serviços**
```bash
# Verificar containers
docker ps | grep vortex

# Verificar logs do Kafka (KRaft: não há ZooKeeper)
docker logs vortex-kafka-simple --tail 50
```

O broker `vortex-kafka-simple` é o `apache/kafka-native`, que não traz as CLIs do Kafka: `docker exec vortex-kafka-simple kafka-topics ...` falha. As CLIs rodam numa execução avulsa da imagem JVM, na rede `vortex-simple`, onde o broker atende em `kafka:19092`:

```bash
# Atalho para as CLIs (scripts em /opt/kafka/bin da imagem JVM)
kcli() { docker run --rm -i --network vortex-simple apache/kafka:4.4.0 "/opt/kafka/bin/$1.sh" --bootstrap-server kafka:19092 "${@:2}"; }

# Verificar saúde do Kafka (o broker responde a um pedido de metadados)
kcli kafka-topics --list > /dev/null && echo "broker respondendo"
```

### **Verificar Conectividade**
```bash
# Listar tópicos
kcli kafka-topics --list

# Criar tópico de teste
kcli kafka-topics --create --topic test --partitions 1 --replication-factor 1

# Produzir mensagem de teste
echo "test message" | kcli kafka-console-producer --topic test

# Consumir mensagem de teste
kcli kafka-console-consumer --topic test --from-beginning --max-messages 1
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
- Verifique se o cluster `vortex-simple` aparece
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
- Vá para Topics → `vortex.movimento.estoque`
- Verifique se as mensagens estão sendo produzidas

---

## 🔧 **Problemas Específicos e Soluções**

### **Problema: "node already exists and owner does not match current session"**
**Solução:** esse erro era do ZooKeeper, que saiu: o broker agora roda em KRaft e guarda os dados na camada do container, que somem no `down`. Recriar o container basta:
```bash
docker compose -f infra/docker/docker-compose.kafka-simple.yml down
docker compose -f infra/docker/docker-compose.kafka-simple.yml up -d kafka
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
tail -f backend/vortex-application-service/logs/application.log | grep -i kafka

# Testar conectividade manual
telnet localhost 9092
```

### **Problema: Tópicos não são criados automaticamente**
**Solução:**
```bash
# Criar tópicos manualmente (kcli: o atalho da seção "Diagnóstico de Problemas")
kcli kafka-topics --create --topic vortex.movimento.estoque --partitions 3 --replication-factor 1
kcli kafka-topics --create --topic vortex.produto.events --partitions 2 --replication-factor 1
kcli kafka-topics --create --topic vortex.alertas.estoque --partitions 1 --replication-factor 1
kcli kafka-topics --create --topic vortex.auditoria --partitions 2 --replication-factor 1
```

---

## 📊 **Monitoramento e Logs**

### **Logs Importantes**
```bash
# Logs da aplicação Spring Boot
tail -f backend/vortex-application-service/logs/application.log | grep -E "(KAFKA|ERROR|WARN)"

# Logs do Kafka
docker logs vortex-kafka-simple -f | grep -E "(ERROR|WARN)"

# Logs do ZooKeeper
docker logs vortex-zookeeper-simple -f | grep -E "(ERROR|WARN)"
```

### **Métricas no Kafka UI**
- **Brokers**: Deve mostrar 1 broker ativo
- **Topics**: Devem aparecer os tópicos do VORTEX
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
   docker logs vortex-kafka-simple > kafka.log 2>&1
   docker logs vortex-zookeeper-simple > zookeeper.log 2>&1
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