# Integração Amazon SQS - VORTEX Sistema de Estoque

## 📋 Visão Geral

Este documento descreve como o sistema VORTEX foi integrado com Amazon SQS para processamento assíncrono de operações de estoque, notificações e auditoria.

## 🏗️ Arquitetura da Integração

### Componentes Principais

1. **SqsConfig**: Configuração dos beans AWS SQS (utiliza `AmazonSQSAsync` para performance otimizada)
2. **SqsProducerService**: Serviço para enviar mensagens para filas
3. **SqsConsumerService**: Serviço para consumir e processar mensagens
4. **MovimentoEstoqueMessageDTO**: DTO específico para mensagens SQS

### Detalhes Técnicos da Configuração

#### SqsConfig - Configuração Assíncrona
```java
@Configuration
public class SqsConfig {
    
    @Bean
    public AmazonSQSAsync amazonSQS() {
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
        
        return AmazonSQSAsyncClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .withRegion(Regions.fromName(region))
                .build();
    }
    
    @Bean
    public QueueMessagingTemplate queueMessagingTemplate(AmazonSQSAsync amazonSQS) {
        return new QueueMessagingTemplate(amazonSQS);
    }
}
```

**Pontos Importantes**:
- Utiliza `AmazonSQSAsync` em vez de `AmazonSQS` para melhor performance
- `QueueMessagingTemplate` e `QueueMessageHandlerFactory` requerem interface assíncrona
- Compatível com Spring Cloud AWS Messaging 2.2.6.RELEASE

### Filas Configuradas

| Fila | Propósito | Dead Letter Queue |
|------|-----------|-------------------|
| `vortex-movimento-estoque-queue` | Processamento assíncrono de movimentações | `vortex-movimento-estoque-dlq` |
| `vortex-notificacao-estoque-queue` | Notificações de estoque baixo/esgotado | `vortex-notificacao-estoque-dlq` |
| `vortex-auditoria-queue` | Log assíncrono de operações | `vortex-auditoria-dlq` |

## ⚙️ Configuração

### 1. Variáveis de Ambiente AWS

```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

### 2. Configurações no application.properties

```properties
# AWS SQS Configuration
cloud.aws.region.static=us-east-1
cloud.aws.stack.auto=false
cloud.aws.credentials.access-key=${AWS_ACCESS_KEY_ID:your-access-key}
cloud.aws.credentials.secret-key=${AWS_SECRET_ACCESS_KEY:your-secret-key}

# SQS Queue Names
sqs.queue.movimento-estoque=vortex-movimento-estoque-queue
sqs.queue.notificacao-estoque=vortex-notificacao-estoque-queue
sqs.queue.auditoria=vortex-auditoria-queue

# Habilitar processamento assíncrono
sqs.processamento.assincrono.enabled=true
```

### 3. Criação das Filas no AWS Console

```bash
# Criar filas principais
aws sqs create-queue --queue-name vortex-movimento-estoque-queue
aws sqs create-queue --queue-name vortex-notificacao-estoque-queue
aws sqs create-queue --queue-name vortex-auditoria-queue

# Criar Dead Letter Queues
aws sqs create-queue --queue-name vortex-movimento-estoque-dlq
aws sqs create-queue --queue-name vortex-notificacao-estoque-dlq
aws sqs create-queue --queue-name vortex-auditoria-dlq
```

## 🚀 Funcionalidades Implementadas

### 1. Processamento Assíncrono de Movimentações

**Quando habilitado** (`sqs.processamento.assincrono.enabled=true`):
- Movimentações são enviadas para SQS em vez de processadas imediatamente
- Permite maior throughput e resilência
- Processamento em background com retry automático

**Fluxo**:
1. Cliente faz POST para `/api/movimentos`
2. Sistema valida dados básicos
3. Envia mensagem para `vortex-movimento-estoque-queue`
4. Retorna resposta imediata ao cliente
5. Consumer processa movimento assincronamente
6. Atualiza estoque e registra movimento

### 2. Notificações Automáticas

**Tipos de Notificação**:
- **Estoque Baixo**: Quando quantidade < 10 unidades
- **Produto Esgotado**: Quando quantidade = 0

**Exemplo de Uso**:
```java
// Enviado automaticamente quando estoque fica baixo
sqsProducerService.enviarNotificacaoEstoqueBaixo(produtoId, quantidadeAtual, quantidadeMinima);
```

### 3. Auditoria Assíncrona

Todas as operações geram logs de auditoria processados assincronamente:

```java
sqsProducerService.enviarAuditoria(
    "MOVIMENTO_PROCESSADO",
    "MovimentoEstoque", 
    movimentoId,
    usuarioId,
    "Detalhes da operação..."
);
```

## 📊 Monitoramento e Observabilidade

### Logs Estruturados

O sistema gera logs estruturados para todas as operações SQS:

```
INFO  - Enviando mensagem de movimento de estoque para SQS. OperationId: abc123, Produto: 1, Tipo: ENTRADA
INFO  - Processando movimento de estoque assíncrono. OperationId: abc123, Produto: 1, Tipo: ENTRADA
WARN  - ALERTA: Estoque baixo detectado - Produto ID: 1, Quantidade atual: 5, Mínima: 10
```

### Métricas Recomendadas

- **Throughput**: Mensagens processadas por segundo
- **Latência**: Tempo entre envio e processamento
- **Taxa de Erro**: Mensagens enviadas para DLQ
- **Backlog**: Mensagens pendentes nas filas

## 🔧 Casos de Uso Práticos

### 1. Processamento de Lotes

Para importar grandes volumes de produtos:

```java
// Habilitar processamento assíncrono
sqs.processamento.assincrono.enabled=true

// Cada movimentação será processada assincronamente
for (MovimentoEstoqueDTO movimento : loteMovimentos) {
    movimentoEstoqueService.criar(movimento, "sistema-importacao");
}
```

### 2. Integração com Sistemas Externos

```java
@SqsListener("${sqs.queue.notificacao-estoque}")
public void processarNotificacaoEstoque(@Payload String payload, @Header("tipo") String tipo) {
    if ("ESTOQUE_BAIXO".equals(tipo)) {
        // Integrar com sistema de compras
        sistemaCompras.criarPedidoReposicao(produtoId);
        
        // Enviar email para gestores
        emailService.enviarAlertaEstoque(payload);
        
        // Atualizar dashboard em tempo real
        websocketService.enviarNotificacao(payload);
    }
}
```

### 3. Processamento com Prioridade

```java
// Movimentações críticas com alta prioridade
MovimentoEstoqueMessageDTO message = new MovimentoEstoqueMessageDTO();
message.setPrioridade("HIGH");
sqsProducerService.enviarMovimentoEstoque(message);
```

## 🛡️ Tratamento de Erros

### Dead Letter Queue (DLQ)

Mensagens que falham após 3 tentativas são enviadas para DLQ:

```java
// Configuração de retry
if (message.getTentativas() >= 3) {
    message.setMotivoErro("Número máximo de tentativas excedido");
    enviarParaDLQ(message);
}
```

### Tipos de Erro Tratados

1. **Estoque Insuficiente**: Enviado para DLQ imediatamente
2. **Produto Não Encontrado**: Enviado para DLQ imediatamente  
3. **Erros Temporários**: Retry até 3 tentativas

## 🔄 Migração e Compatibilidade

### Configuração de Processamento

O sistema mantém compatibilidade total:

- **Processamento Síncrono**: `sqs.processamento.assincrono.enabled=false` (padrão)
- **Processamento Assíncrono**: `sqs.processamento.assincrono.enabled=true`

### Rollback Seguro

Para voltar ao processamento síncrono:

1. Alterar configuração: `sqs.processamento.assincrono.enabled=false`
2. Aguardar processamento das mensagens pendentes
3. Reiniciar aplicação

## 📈 Benefícios da Integração

### Performance
- **Throughput**: Até 10x maior para operações em lote
- **Responsividade**: API responde imediatamente
- **Escalabilidade**: Processamento distribuído

### Resilência
- **Retry Automático**: Reprocessamento em caso de falha
- **Dead Letter Queue**: Isolamento de mensagens problemáticas
- **Graceful Degradation**: Fallback para processamento síncrono

### Observabilidade
- **Rastreamento**: OperationId único para cada operação
- **Auditoria**: Log completo de todas as operações
- **Monitoramento**: Métricas detalhadas via CloudWatch

## 🧪 Testes

### Teste Local com LocalStack

```bash
# Instalar LocalStack
pip install localstack

# Iniciar LocalStack
localstack start

# Configurar endpoint local
cloud.aws.sqs.endpoint=http://localhost:4566
```

### Testes de Integração

```java
@Test
void deveProcessarMovimentoAssincronamente() {
    // Habilitar processamento assíncrono
    ReflectionTestUtils.setField(movimentoEstoqueService, "processamentoAssincronoEnabled", true);
    
    // Criar movimento
    MovimentoEstoqueDTO movimento = criarMovimentoTeste();
    MovimentoEstoqueDTO resultado = movimentoEstoqueService.criar(movimento, "teste");
    
    // Verificar que foi enviado para SQS (sem ID de movimento)
    assertNull(resultado.getId());
    assertNotNull(resultado.getDataMovimento());
}
```

## 🔧 Troubleshooting

### Erro de Compilação: "incompatible types: AmazonSQS cannot be converted to AmazonSQSAsync"

**Problema**: Erro comum ao configurar Spring Cloud AWS Messaging.

**Causa**: `QueueMessagingTemplate` e `QueueMessageHandlerFactory` requerem `AmazonSQSAsync`.

**Solução**:
```java
// ❌ INCORRETO - Causa erro de compilação
@Bean
public AmazonSQS amazonSQS() {
    return AmazonSQSClientBuilder.standard()...
}

// ✅ CORRETO - Usar interface assíncrona
@Bean
public AmazonSQSAsync amazonSQS() {
    return AmazonSQSAsyncClientBuilder.standard()...
}
```

### Outros Problemas Comuns

1. **Mensagens não são processadas**
   - Verificar se as filas existem no AWS
   - Validar credenciais AWS
   - Confirmar região configurada

2. **Timeout em operações**
   - Aumentar timeout do cliente SQS
   - Verificar conectividade de rede
   - Monitorar latência AWS

3. **Muitas mensagens na DLQ**
   - Revisar lógica de processamento
   - Verificar logs de erro
   - Ajustar número de tentativas

## 🚨 Considerações de Produção

### Segurança
- Usar IAM Roles em vez de Access Keys
- Configurar VPC Endpoints para SQS
- Criptografia em trânsito e em repouso

### Monitoramento
- Configurar CloudWatch Alarms
- Monitorar DLQ para identificar problemas
- Configurar SNS para alertas críticos

### Custos
- Monitorar número de mensagens processadas
- Configurar retention period adequado
- Usar batch processing quando possível

---

**Nota**: Esta integração foi projetada para ser não-invasiva e manter total compatibilidade com o sistema existente, permitindo adoção gradual do processamento assíncrono conforme necessário. 