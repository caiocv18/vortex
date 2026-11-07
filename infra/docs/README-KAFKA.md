# Integração Apache Kafka - VORTEX Sistema de Estoque

## 📋 Visão Geral

Este documento descreve a integração do sistema VORTEX com Apache Kafka para processamento de eventos em tempo real, event sourcing e integração com sistemas externos.

## 🏗️ Arquitetura da Integração

### Fluxo de Eventos

```mermaid
graph TB
    subgraph "VORTEX Backend"
        A[Controllers] --> B[Services]
        B --> C[KafkaProducerService]
        C --> D[Kafka Topics]
        D --> E[KafkaConsumerService]
        E --> F[Serviços de Integração]
    end
    
    subgraph "Kafka Cluster"
        D1[vortex.movimento.estoque]
        D2[vortex.produto.events]
        D3[vortex.alertas.estoque]
        D4[vortex.auditoria]
        D5[vortex.relatorios.events]
    end
    
    subgraph "Integrações"
        F1[Analytics Service]
        F2[Notification Service]
        F3[External Integration]
        F4[Dashboard Updates]
    end
    
    C --> D1
    C --> D2
    C --> D3
    C --> D4
    C --> D5
    
    E --> F1
    E --> F2
    E --> F3
    E --> F4
```

## 🚀 Casos de Uso Implementados

### 1. **Event Sourcing para Movimentações de Estoque**

**Problema Resolvido**: Rastreabilidade completa de todas as operações de estoque

**Como Funciona**:
- Toda movimentação de estoque gera um evento no tópico `vortex.movimento.estoque`
- Eventos contêm informações completas da operação (antes/depois)
- Permite reconstruir o estado do estoque em qualquer momento
- Facilita auditoria e compliance

**Exemplo de Evento**:
```json
{
  "eventId": "uuid-123",
  "timestamp": "2024-01-15T10:30:00",
  "eventType": "MOVIMENTO_ESTOQUE",
  "movimentoId": 123,
  "produtoId": 456,
  "tipoMovimentacao": "SAIDA",
  "quantidadeMovimentada": 10,
  "estoqueAnterior": 50,
  "estoqueAtual": 40,
  "lucro": 150.00
}
```

### 2. **Alertas de Estoque em Tempo Real**

**Problema Resolvido**: Detecção proativa de problemas de estoque

**Como Funciona**:
- Sistema monitora níveis de estoque após cada movimentação
- Gera alertas automáticos quando estoque fica baixo/esgotado
- Permite ações automáticas (pedidos de reposição, notificações)

**Tipos de Alerta**:
- `ESTOQUE_BAIXO`: Quantidade < 10 unidades
- `ESTOQUE_ESGOTADO`: Quantidade = 0
- `ESTOQUE_CRITICO`: Quantidade < 5 unidades

### 3. **Integração com Sistemas Externos**

**Problema Resolvido**: Sincronização automática com ERP, WMS e outros sistemas

**Como Funciona**:
- Eventos são consumidos por serviços de integração
- Sincronização automática com sistemas externos
- Criação automática de pedidos de reposição
- Atualização de catálogos de produtos

### 4. **Analytics e Dashboards em Tempo Real**

**Problema Resolvido**: Visibilidade imediata de métricas de negócio

**Como Funciona**:
- Processamento de eventos para cálculo de métricas
- Atualização de dashboards via WebSocket
- Geração de insights de negócio

## 🛠️ Como Usar

### Configuração Inicial

1. **Iniciar o Kafka**:
```bash
# Pelo launcher (sobe o broker do docker-compose.kafka-simple.yml)
./start-vortex.sh -e dev -m kafka

# Ou direto pelo Docker Compose v2
docker compose -f infra/docker/docker-compose.kafka-simple.yml up -d kafka

# Verificar se está funcionando
docker compose -f infra/docker/docker-compose.kafka-simple.yml ps
```

O broker é o `apache/kafka-native:4.4.0`: Kafka 4.4 compilado com GraalVM (cerca de 130 a 250 MB de RAM, contra uns 680 MB da imagem JVM), em **KRaft de nó único**, sem ZooKeeper. O mesmo processo é broker e controller; os listeners são `localhost:9092` para clientes no host e `kafka:19092` para containers na rede `vortex-simple`. Os dados ficam na camada do container e somem no `docker compose down`; os tópicos da aplicação são recriados pelo próprio backend (`KafkaConfig`) e o auto-create está ligado.

A imagem nativa não traz as CLIs do Kafka. Para listar tópicos, use a imagem JVM numa execução avulsa:
```bash
docker run --rm --network vortex-simple apache/kafka:4.4.0 \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list
```

| Arquivo | Uso |
|---------|-----|
| `docker-compose.kafka-simple.yml` | o do launcher: só o broker, mais a Kafka UI no profile `ui` |
| `docker-compose.kafka.yml` | broker + Confluent Platform 8.4 (Connect, Schema Registry na 8085, ksqlDB, ferramentas), cada um no seu profile |
| `docker-compose.full-kafka.yml` | stack prd completa (Oracle, backend e frontend em container) |

2. **Configurar o Application Properties**:
```properties
# Habilitar Kafka
kafka.enabled=true
spring.kafka.bootstrap-servers=localhost:9092

# Usar perfil específico do Kafka
spring.profiles.active=kafka
```

3. **Executar a Aplicação**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=kafka
```

### Monitoramento

**Kafka UI** (kafbat/kafka-ui v1.5.0, sucessora da provectuslabs/kafka-ui): http://localhost:8090
- Só sobe com o profile `ui`: `docker compose -f infra/docker/docker-compose.kafka-simple.yml --profile ui up -d kafka-ui`
- Visualizar tópicos e mensagens
- Monitorar consumers e producers
- Verificar lag de consumo

**Health da Aplicação**: http://localhost:8080/health
- O backend não usa Spring Boot Actuator; o health é um controller próprio

### Testando a Integração

1. **Criar um Produto**:
```bash
curl -X POST http://localhost:8080/api/produtos \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Produto Teste Kafka",
    "valorFornecedor": 10.00,
    "quantidadeEmEstoque": 100,
    "tipoProdutoId": 1
  }'
```

2. **Verificar Evento no Kafka UI**:
- Acessar http://localhost:8090 (profile `ui`)
- Navegar para o tópico `vortex.produto.events`
- Verificar se o evento foi publicado

3. **Fazer uma Movimentação**:
```bash
curl -X POST http://localhost:8080/api/movimentos \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId": 1,
    "tipoMovimentacao": "SAIDA",
    "quantidadeMovimentada": 50
  }'
```

4. **Verificar Eventos Gerados**:
- Tópico `vortex.movimento.estoque`: Evento da movimentação
- Tópico `vortex.alertas.estoque`: Alerta se estoque ficou baixo

## 📊 Tópicos Kafka

| Tópico | Descrição | Partições | Retenção |
|--------|-----------|-----------|----------|
| `vortex.movimento.estoque` | Eventos de movimentação | 3 | 7 dias |
| `vortex.produto.events` | Eventos de produtos | 2 | 30 dias |
| `vortex.alertas.estoque` | Alertas de estoque | 1 | 3 dias |
| `vortex.auditoria` | Eventos de auditoria | 2 | 180 dias |
| `vortex.relatorios.events` | Eventos de relatórios | 1 | 1 dia |

## 🔧 Cenários Avançados

### 1. **Processamento de Lotes com Kafka**

Para importar grandes volumes de dados:

```java
@Service
public class ImportacaoService {
    
    @Autowired
    private KafkaProducerService kafkaProducer;
    
    public void importarLoteMovimentos(List<MovimentoDTO> movimentos) {
        // Processar em lotes para melhor performance
        movimentos.parallelStream()
            .forEach(movimento -> {
                // Cada movimento gera evento no Kafka
                // Processamento assíncrono via consumers
                movimentoService.criar(movimento);
            });
    }
}
```

### 2. **Integração com Data Lake**

```java
@KafkaListener(topics = "vortex.movimento.estoque")
public void enviarParaDataLake(MovimentoEstoqueEventDTO event) {
    // Enviar dados para S3, BigQuery, etc.
    dataLakeService.enviarEvento(event);
}
```

### 3. **Machine Learning em Tempo Real**

```java
@KafkaListener(topics = "vortex.movimento.estoque")
public void processarML(MovimentoEstoqueEventDTO event) {
    // Alimentar modelos de ML para previsão de demanda
    mlService.processarMovimento(event);
    
    // Gerar recomendações de reposição
    if (mlService.shouldReorder(event.getProdutoId())) {
        recomendacaoService.gerarPedidoReposicao(event);
    }
}
```

### 4. **Kafka Streams para Agregações**

```java
@Component
public class EstoqueStreams {
    
    @Bean
    public KStream<String, MovimentoEstoqueEventDTO> processarMovimentos() {
        StreamsBuilder builder = new StreamsBuilder();
        
        KStream<String, MovimentoEstoqueEventDTO> movimentos = 
            builder.stream("vortex.movimento.estoque");
            
        // Calcular métricas em tempo real
        movimentos
            .groupByKey()
            .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
            .aggregate(
                EstoqueMetrics::new,
                (key, movimento, metrics) -> metrics.add(movimento),
                Materialized.as("estoque-metrics-store")
            )
            .toStream()
            .to("vortex.metricas.tempo-real");
            
        return movimentos;
    }
}
```

## 🔄 Comparação: Kafka vs SQS

| Aspecto | Kafka | SQS |
|---------|-------|-----|
| **Controle** | Total (self-hosted) | Gerenciado pela AWS |
| **Performance** | Muito alta | Alta |
| **Durabilidade** | Configurável | Até 14 dias |
| **Ordem** | Garantida por partição | FIFO limitado |
| **Replay** | Sim | Não |
| **Custo** | Infraestrutura própria | Pay-per-use |
| **Complexidade** | Maior | Menor |

## 📈 Benefícios da Integração

### Para o Negócio
- **Visibilidade em Tempo Real**: Dashboards atualizados instantaneamente
- **Proatividade**: Alertas automáticos de estoque
- **Integração**: Sincronização automática com sistemas externos
- **Auditoria**: Rastreabilidade completa de operações

### Para a Tecnologia
- **Escalabilidade**: Processamento distribuído de eventos
- **Resiliência**: Tolerância a falhas e reprocessamento
- **Flexibilidade**: Novos consumers sem impacto no sistema
- **Performance**: Processamento assíncrono de alta performance

## 🚦 Monitoramento e Alertas

### Métricas Importantes
- **Lag de Consumo**: Atraso no processamento de mensagens
- **Throughput**: Mensagens por segundo
- **Erro Rate**: Taxa de erros no processamento
- **Latência**: Tempo de processamento end-to-end

### Alertas Configurados
- Lag de consumo > 1000 mensagens
- Taxa de erro > 5%
- Latência > 30 segundos
- Espaço em disco Kafka > 80%

## 🔧 Troubleshooting

### Problemas Comuns

1. **Consumer Lag Alto**:
   - Aumentar número de partições
   - Aumentar paralelismo dos consumers
   - Otimizar processamento dos eventos

2. **Perda de Mensagens**:
   - Verificar configuração de `acks=all`
   - Verificar `enable.idempotence=true`
   - Revisar tratamento de exceções

3. **Duplicação de Mensagens**:
   - Implementar processamento idempotente
   - Verificar configuração de retry
   - Usar chaves de deduplicação

### Comandos Úteis

```bash
# Listar tópicos
docker exec vortex-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Verificar consumer groups
docker exec vortex-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list

# Ver detalhes de um consumer group
docker exec vortex-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --group vortex-inventory-group --describe

# Consumir mensagens de um tópico
docker exec vortex-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic vortex.movimento.estoque --from-beginning
```

## 🎯 Próximos Passos

1. **Implementar Schema Registry** para versionamento de eventos
2. **Adicionar Kafka Streams** para processamento em tempo real
3. **Integrar com Elasticsearch** para busca de eventos
4. **Implementar CQRS** usando Kafka como event store
5. **Adicionar métricas customizadas** com Micrometer

---

Para mais informações, consulte a documentação oficial do [Apache Kafka](https://kafka.apache.org/documentation/) e [Spring Kafka](https://spring.io/projects/spring-kafka). 