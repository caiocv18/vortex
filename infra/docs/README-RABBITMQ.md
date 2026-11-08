# 🐰 RabbitMQ - Sistema de Mensageria Vortex

Esta documentação descreve como configurar e usar o RabbitMQ como sistema de mensageria no projeto Vortex.

## 📋 Índice

- [Visão Geral](#visão-geral)
- [Configuração](#configuração)
- [Arquitetura](#arquitetura)
- [Como Usar](#como-usar)
- [Monitoramento](#monitoramento)
- [Troubleshooting](#troubleshooting)
- [Comandos Úteis](#comandos-úteis)

## 🎯 Visão Geral

O RabbitMQ é um message broker robusto que implementa o protocolo AMQP (Advanced Message Queuing Protocol). No Vortex, ele é usado para:

- **Processamento Assíncrono**: Movimentações de estoque processadas em background
- **Notificações**: Alertas de estoque baixo, esgotado, etc.
- **Integração**: Sincronização com sistemas externos
- **Auditoria**: Registro de eventos para compliance

### Vantagens do RabbitMQ

- **Confiabilidade**: Garantia de entrega de mensagens
- **Flexibilidade**: Múltiplos padrões de mensageria (direct, topic, fanout)
- **Interface Gráfica**: Management UI para monitoramento
- **Clustering**: Suporte a alta disponibilidade
- **Protocolos**: AMQP, MQTT, STOMP, HTTP

## ⚙️ Configuração

### 1. Configuração Docker

O RabbitMQ é executado via Docker Compose:

```yaml
# infra/docker/docker-compose.rabbitmq.yml
services:
  rabbitmq:
    image: rabbitmq:4.4.0-management-alpine
    container_name: vortex-rabbitmq
    ports:
      - "5672:5672"     # AMQP port
      - "15672:15672"   # Management UI port
    environment:
      RABBITMQ_DEFAULT_USER: vortex
      RABBITMQ_DEFAULT_PASS: vortex123
      RABBITMQ_DEFAULT_VHOST: vortex-vhost
    volumes:
      - ../rabbitmq/20-vortex.conf:/etc/rabbitmq/conf.d/20-vortex.conf:ro
    mem_limit: 256m
```

O `infra/rabbitmq/20-vortex.conf` fixa o alarme de memória em 160 MiB (o RabbitMQ mede a RAM do host, não o `mem_limit` do container) e baixa o `disk_free_limit` de 2 GB para 256 MB, que travava os publishers num disco de WSL com pouco espaço livre. O antigo `RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS` com `log_levels` saiu: o RabbitMQ 4.x não lê mais essa chave, e o nível de log de conexão foi para o mesmo arquivo.

### 2. Configuração Spring Boot

```properties
# application-rabbitmq.properties

# Configurações de Conexão
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=vortex
spring.rabbitmq.password=vortex123
spring.rabbitmq.virtual-host=vortex-vhost

# Configurações do Message Broker
message.broker.type=rabbitmq
message.broker.enabled=true

# Configurações de Listener
spring.rabbitmq.listener.simple.concurrency=3
spring.rabbitmq.listener.simple.max-concurrency=10
spring.rabbitmq.listener.simple.acknowledge-mode=manual
spring.rabbitmq.listener.simple.retry.enabled=true
# Boot 4: max-attempts virou max-retries (só as novas tentativas; 2 = 3 entregas no total)
spring.rabbitmq.listener.simple.retry.max-retries=2
```

### 3. Configuração do Factory Pattern

```java
// MessageBrokerFactory escolhe RabbitMQ baseado na configuração
@Value("${message.broker.type:kafka}")
private String brokerType;

if ("rabbitmq".equals(brokerType)) {
    return rabbitMQProducerService;
}
```

## 🏗️ Arquitetura

### Exchanges e Queues

```
📨 EXCHANGES:
├── vortex.movimento.exchange (topic)
├── vortex.produto.exchange (topic)  
├── vortex.alerta.exchange (topic)
└── vortex.auditoria.exchange (topic)

📋 QUEUES:
├── vortex.movimento.queue
├── vortex.produto.queue
├── vortex.alerta.queue
└── vortex.auditoria.queue

🔗 ROUTING KEYS:
├── movimento.estoque.entrada
├── movimento.estoque.saida
├── produto.criado
├── produto.atualizado
├── alerta.estoque.baixo
├── alerta.estoque.esgotado
└── auditoria.*
```

### Fluxo de Mensagens

1. **Produção**: Serviços publicam eventos nos exchanges
2. **Roteamento**: Exchanges roteiam para queues baseado em routing keys
3. **Consumo**: Listeners consomem mensagens das queues
4. **Processamento**: Lógica de negócio processa eventos
5. **Confirmação**: ACK/NACK para garantir entrega

## 🚀 Como Usar

### Inicialização

```bash
# 1. Iniciar apenas RabbitMQ
./infra/scripts/start-rabbitmq.sh

# 2. Iniciar sistema completo com RabbitMQ
./start-vortex.sh -e dev -m rabbitmq -c full

# 3. Modo não interativo
./start-vortex.sh --no-interaction -e dev -m rabbitmq

# 4. Ambiente prd (backend no Docker), com um JWT_SECRET forte
export JWT_SECRET="$(openssl rand -base64 48)"
./start-vortex.sh -e prd -m rabbitmq
```

No `-e prd`, o backend roda no container `vortex-app` (`infra/docker/docker-compose.yml`). Ele entra na rede `vortex-rabbitmq-network` do `docker-compose.rabbitmq.yml` e recebe `RABBITMQ_HOST=vortex-rabbitmq`, porta 5672, usuário `vortex`, senha `vortex123` e vhost `vortex-vhost`; o launcher liga a integração com `RABBITMQ_ENABLED=true` junto do perfil `prd,rabbitmq`. Sem `-m rabbitmq`, `RABBITMQ_ENABLED` fica `false` e o backend não tenta o broker.

### Acessar Management UI

```
URL: http://localhost:15672
Usuário: vortex
Senha: vortex123
Virtual Host: vortex-vhost
```

### Testando a Integração

1. **Criar um Produto**:
```bash
curl -X POST http://localhost:8080/api/produtos \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Produto Teste RabbitMQ",
    "valorFornecedor": 15.00,
    "quantidadeEmEstoque": 50,
    "tipoProdutoId": 1
  }'
```

2. **Verificar no Management UI**:
   - Acessar http://localhost:15672
   - Ir para "Queues" 
   - Verificar mensagens em `vortex.produto.queue`

3. **Fazer uma Movimentação**:
```bash
curl -X POST http://localhost:8080/api/movimentos \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId": 1,
    "tipoMovimentacao": "SAIDA",
    "quantidadeMovimentada": 30
  }'
```

4. **Verificar Eventos Gerados**:
   - Queue `vortex.movimento.queue`: Evento da movimentação
   - Queue `vortex.alerta.queue`: Alerta se estoque ficou baixo

## 📊 Monitoramento

### Management UI

**Visão Geral**: http://localhost:15672
- **Overview**: Métricas gerais do RabbitMQ
- **Connections**: Conexões ativas
- **Channels**: Canais de comunicação
- **Exchanges**: Exchanges configurados
- **Queues**: Filas e suas métricas
- **Admin**: Gerenciamento de usuários e vhosts

### Métricas Importantes

- **Message Rate**: Mensagens por segundo
- **Queue Length**: Número de mensagens na fila
- **Consumer Count**: Número de consumidores ativos
- **Memory Usage**: Uso de memória do RabbitMQ
- **Disk Usage**: Uso de disco para persistência

### Health Checks

```bash
# Verificar status do RabbitMQ
docker exec vortex-rabbitmq rabbitmq-diagnostics status

# Verificar se está pronto para conexões
docker exec vortex-rabbitmq rabbitmq-diagnostics ping
```

## 🛠️ Comandos Úteis

### Gerenciamento de Queues

```bash
# Listar todas as filas
docker exec vortex-rabbitmq rabbitmqctl list_queues

# Listar filas com detalhes
docker exec vortex-rabbitmq rabbitmqctl list_queues name messages consumers

# Purgar uma fila
docker exec vortex-rabbitmq rabbitmqctl purge_queue vortex.movimento.queue
```

### Gerenciamento de Exchanges

```bash
# Listar exchanges
docker exec vortex-rabbitmq rabbitmqctl list_exchanges

# Listar bindings
docker exec vortex-rabbitmq rabbitmqctl list_bindings
```

### Gerenciamento de Conexões

```bash
# Listar conexões ativas
docker exec vortex-rabbitmq rabbitmqctl list_connections

# Listar canais
docker exec vortex-rabbitmq rabbitmqctl list_channels
```

### Logs e Debug

```bash
# Ver logs do RabbitMQ
docker logs vortex-rabbitmq -f

# Habilitar logs de debug
docker exec vortex-rabbitmq rabbitmqctl set_log_level debug
```

## 🔧 Troubleshooting

### Problemas Comuns

#### 1. RabbitMQ não inicia

**Sintomas**: Container não sobe ou para imediatamente

**Soluções**:
```bash
# Verificar logs
docker logs vortex-rabbitmq

# Verificar portas ocupadas
lsof -i :5672
lsof -i :15672

# Limpar dados corrompidos (atenção: -v apaga as filas persistidas)
docker compose -f infra/docker/docker-compose.rabbitmq.yml down -v
```

#### 2. Aplicação não conecta

**Sintomas**: Erros de conexão nos logs do Spring Boot

**Soluções**:
```bash
# Verificar se RabbitMQ está rodando
docker ps | grep vortex-rabbitmq

# Testar conectividade
docker exec vortex-rabbitmq rabbitmq-diagnostics ping

# Verificar configurações
docker exec vortex-rabbitmq rabbitmqctl list_vhosts
```

#### 3. Mensagens não são consumidas

**Sintomas**: Mensagens ficam acumuladas nas filas

**Soluções**:
```bash
# Verificar consumidores
docker exec vortex-rabbitmq rabbitmqctl list_consumers

# Verificar logs da aplicação
tail -f backend.log | grep -i rabbit

# Verificar configurações de listener
grep -r "rabbitmq.listener" backend/vortex-application-service/src/main/resources/
```

#### 4. Performance baixa

**Sintomas**: Processamento lento de mensagens

**Soluções**:
```bash
# Aumentar concorrência nos listeners
spring.rabbitmq.listener.simple.concurrency=5
spring.rabbitmq.listener.simple.max-concurrency=20

# Verificar uso de recursos
docker stats vortex-rabbitmq

# Otimizar configurações
spring.rabbitmq.listener.simple.prefetch=10
```

### Comandos de Diagnóstico

```bash
# Status completo do RabbitMQ
docker exec vortex-rabbitmq rabbitmq-diagnostics status

# Verificar configuração
docker exec vortex-rabbitmq rabbitmqctl environment

# Verificar plugins habilitados
docker exec vortex-rabbitmq rabbitmq-plugins list

# Verificar políticas
docker exec vortex-rabbitmq rabbitmqctl list_policies
```

## 🔄 Comparação com Kafka

| Aspecto | RabbitMQ | Kafka |
|---------|----------|-------|
| **Protocolo** | AMQP, MQTT, STOMP | TCP binário customizado |
| **Modelo** | Push (broker envia) | Pull (consumer puxa) |
| **Ordem** | Por fila | Por partição |
| **Persistência** | Opcional | Sempre |
| **Retenção** | Até consumo | Baseada em tempo |
| **Replay** | Limitado | Completo |
| **Complexidade** | Baixa | Média |
| **UI** | Management UI | Kafka UI (externa) |

## 🎯 Cenários de Uso

### Quando usar RabbitMQ

✅ **Ideal para**:
- Processamento de comandos/tarefas
- Notificações em tempo real
- Integração com sistemas legados
- Workflows complexos de roteamento
- Garantia de entrega crítica

### Quando usar Kafka

✅ **Ideal para**:
- Event sourcing
- Streaming de dados
- Analytics em tempo real
- Logs de aplicação
- Reprocessamento de eventos

## 📈 Próximos Passos

- [ ] Configurar clustering para alta disponibilidade
- [ ] Implementar dead letter queues
- [ ] Adicionar métricas customizadas
- [ ] Configurar SSL/TLS
- [ ] Implementar rate limiting
- [ ] Adicionar monitoramento com Prometheus 