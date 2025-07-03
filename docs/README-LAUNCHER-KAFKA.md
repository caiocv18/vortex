# 🚀 VORTEX Launcher - Integração Kafka

## 📋 Visão Geral

O script `start-vortex.sh` foi atualizado para suportar **Apache Kafka** como sistema de mensageria, além do Amazon SQS já existente. Agora você pode escolher entre:

- **Kafka**: Event sourcing e streaming em tempo real
- **SQS**: Processamento assíncrono na AWS
- **Híbrido**: Kafka + SQS para máxima flexibilidade
- **Nenhum**: Processamento síncrono apenas

## 🎯 Novos Recursos

### **1. Seleção de Sistema de Mensageria**

```bash
# Modo interativo - escolha durante execução
./start-vortex.sh

# Especificar diretamente
./start-vortex.sh -e dev -m kafka
./start-vortex.sh -e prd -m both
./start-vortex.sh -e dev -m sqs
./start-vortex.sh -e prd -m none
```

### **2. Gestão Automática do Kafka**

- **Inicialização automática** do cluster Kafka
- **Health checks** para garantir que Kafka está pronto
- **Kafka UI** para monitoramento visual
- **Configuração automática** das variáveis de ambiente

### **3. Integração com Docker Compose**

Novos arquivos de compose:
- `docker-compose.kafka.yml` - Kafka standalone
- `docker-compose.full-kafka.yml` - Stack completa com Kafka

## 🛠️ Como Usar

### **Cenário 1: Desenvolvimento com Kafka**

```bash
# Iniciar em modo desenvolvimento com Kafka
./start-vortex.sh -e dev -m kafka

# Resultado:
# ✅ Kafka rodando em localhost:9092
# ✅ Kafka UI disponível em http://localhost:8090
# ✅ Backend com perfis: dev,kafka
# ✅ Frontend em modo desenvolvimento
```

### **Cenário 2: Produção Híbrida (Kafka + SQS)**

```bash
# Iniciar em modo produção com ambos os sistemas
./start-vortex.sh -e prd -m both --logs

# Resultado:
# ✅ Kafka cluster completo
# ✅ Backend configurado para Kafka + SQS
# ✅ Oracle Database
# ✅ Frontend otimizado
```

### **Cenário 3: Apenas Backend com Kafka**

```bash
# Iniciar apenas backend com Kafka
./start-vortex.sh --backend-only -m kafka

# Resultado:
# ✅ Kafka cluster
# ✅ Backend com integração Kafka
# ❌ Frontend não iniciado
```

## 📊 Status e Monitoramento

O comando de status agora mostra informações detalhadas:

```bash
./start-vortex.sh --status  # (executar sem argumentos para ver status)
```

**Saída de exemplo:**
```
📊 STATUS DOS SERVIÇOS
═══════════════════════════════════════════════════════════════

📨 SISTEMA DE MENSAGERIA (kafka):
   ✅ Kafka rodando
   📡 Broker: localhost:9092
   🌐 Kafka UI: http://localhost:8090
   🔗 Zookeeper: localhost:2181

🔧 BACKEND (dev):
   ✅ Rodando (PID: 12345)
   🌐 API: http://localhost:8081
   📚 Swagger: http://localhost:8081/swagger-ui.html
   🗄️  H2 Console: http://localhost:8081/h2-console

🎨 FRONTEND (dev):
   ✅ Rodando (PID: 12346)
   🌐 App: http://localhost:5173
```

## 🔧 Comandos Úteis

### **Comandos Básicos**
```bash
./start-vortex.sh --help          # Mostrar ajuda
./start-vortex.sh --stop          # Parar todos os serviços
./start-vortex.sh --clean         # Limpar ambiente (perde dados!)
```

### **Comandos Kafka**
```bash
# Logs do Kafka
docker logs vortex-kafka -f

# Listar tópicos
docker exec vortex-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Listar consumer groups
docker exec vortex-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list

# Consumir mensagens de um tópico
docker exec vortex-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vortex.movimento.estoque \
  --from-beginning
```

## 🌐 Portas e Serviços

| Serviço | Porta | URL |
|---------|-------|-----|
| **Backend API** | 8081 | http://localhost:8081 |
| **Frontend** | 5173 (dev) / 4173 (prd) | http://localhost:5173 |
| **Kafka Broker** | 9092 | localhost:9092 |
| **Kafka UI** | 8090 | http://localhost:8090 |
| **Zookeeper** | 2181 | localhost:2181 |
| **Oracle DB** | 1521 | localhost:1521 |

## 📁 Arquivos de Configuração

### **Docker Compose Files**
- `docker-compose.kafka.yml` - Kafka standalone
- `docker-compose.full-kafka.yml` - Stack completa + Kafka
- `docker-compose.full.yml` - Stack completa sem Kafka

### **Spring Profiles**
- `dev` - Desenvolvimento com H2
- `prd` - Produção com Oracle
- `kafka` - Integração Kafka habilitada
- `sqs` - Integração SQS habilitada

### **Combinações de Perfis**
- `dev,kafka` - Desenvolvimento com Kafka
- `prd,kafka,sqs` - Produção híbrida
- `dev` - Desenvolvimento sem mensageria

## 🚦 Troubleshooting

### **Kafka não inicia**
```bash
# Verificar logs
docker logs vortex-kafka

# Verificar se portas estão livres
netstat -an | grep 9092
netstat -an | grep 2181

# Limpar ambiente e tentar novamente
./start-vortex.sh --clean
./start-vortex.sh -e dev -m kafka
```

### **Backend não conecta no Kafka**
```bash
# Verificar configuração
docker exec vortex-app env | grep KAFKA

# Verificar logs do backend
tail -f backend.log

# Ou se rodando no Docker
docker logs vortex-app -f
```

### **Conflito de portas**
- **Kafka UI**: Porta 8090 (era 8080)
- **Backend**: Porta 8081 (era 8080)
- **Frontend**: Porta 5173 (dev) / 4173 (prd)

## 🎯 Exemplos Práticos

### **1. Desenvolvimento Completo**
```bash
# Iniciar ambiente completo de desenvolvimento
./start-vortex.sh -e dev -m kafka

# Testar API
curl http://localhost:8081/api/produtos

# Verificar tópicos Kafka
curl http://localhost:8090
```

### **2. Produção com Monitoramento**
```bash
# Iniciar produção com logs
./start-vortex.sh -e prd -m kafka --logs

# Em outro terminal, monitorar Kafka
docker logs vortex-kafka -f
```

### **3. Teste de Integração**
```bash
# Iniciar apenas backend para testes
./start-vortex.sh --backend-only -m kafka

# Fazer movimentação de estoque
curl -X POST http://localhost:8081/api/movimentos \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId": 1,
    "tipoMovimentacao": "SAIDA",
    "quantidadeMovimentada": 10
  }'

# Verificar eventos no Kafka UI
# http://localhost:8090
```

## 📈 Benefícios da Integração

### **Para Desenvolvimento**
- **Setup automático** do Kafka
- **Hot reload** mantido
- **Logs centralizados**
- **UI visual** para debug

### **Para Produção**
- **Stack completa** em containers
- **Health checks** automáticos
- **Configuração consistente**
- **Monitoramento integrado**

### **Para Operações**
- **Comandos padronizados**
- **Cleanup automático**
- **Status centralizado**
- **Troubleshooting simplificado**

---

## 🚀 Próximos Passos

1. **Executar** o ambiente desejado
2. **Testar** as funcionalidades
3. **Monitorar** via Kafka UI
4. **Expandir** conforme necessário

Para mais detalhes sobre a integração Kafka, consulte: [README-KAFKA.md](backend/README-KAFKA.md) 