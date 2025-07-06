package br.com.vortex.desafio.backend.service;

import br.com.vortex.desafio.backend.dto.MovimentoEstoqueMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.aws.messaging.core.QueueMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Serviço responsável por enviar mensagens para filas Amazon SQS.
 * Centraliza a lógica de produção de mensagens para diferentes tipos de operações.
 */
@Slf4j
@Service
public class SqsProducerService {

    private final QueueMessagingTemplate queueMessagingTemplate;

    @Value("${sqs.queue.movimento-estoque}")
    private String movimentoEstoqueQueue;

    @Value("${sqs.queue.notificacao-estoque}")
    private String notificacaoEstoqueQueue;

    @Value("${sqs.queue.auditoria}")
    private String auditoriaQueue;

    @Autowired
    public SqsProducerService(QueueMessagingTemplate queueMessagingTemplate) {
        this.queueMessagingTemplate = queueMessagingTemplate;
    }

    /**
     * Envia mensagem de movimento de estoque para processamento assíncrono.
     *
     * @param message DTO com dados do movimento
     */
    public void enviarMovimentoEstoque(MovimentoEstoqueMessageDTO message) {
        try {
            // Gera ID único para rastreamento
            if (message.getOperationId() == null) {
                message.setOperationId(UUID.randomUUID().toString());
            }
            
            // Define timestamp se não existir
            if (message.getTimestamp() == null) {
                message.setTimestamp(LocalDateTime.now());
            }

            log.info("Enviando mensagem de movimento de estoque para SQS. OperationId: {}, Produto: {}, Tipo: {}", 
                    message.getOperationId(), message.getProdutoId(), message.getTipoMovimentacao());

            queueMessagingTemplate.convertAndSend(movimentoEstoqueQueue, message);
            
            log.info("Mensagem enviada com sucesso para fila: {}", movimentoEstoqueQueue);
            
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem para SQS. OperationId: {}, Erro: {}", 
                    message.getOperationId(), e.getMessage(), e);
            throw new RuntimeException("Falha ao enviar mensagem para SQS", e);
        }
    }

    /**
     * Envia notificação de estoque baixo.
     *
     * @param produtoId ID do produto
     * @param quantidadeAtual Quantidade atual em estoque
     * @param quantidadeMinima Quantidade mínima configurada
     */
    public void enviarNotificacaoEstoqueBaixo(Long produtoId, Integer quantidadeAtual, Integer quantidadeMinima) {
        try {
            var notificacao = MessageBuilder.withPayload(
                    String.format("Estoque baixo - Produto ID: %d, Quantidade atual: %d, Mínima: %d", 
                            produtoId, quantidadeAtual, quantidadeMinima))
                    .setHeader("produtoId", produtoId)
                    .setHeader("tipo", "ESTOQUE_BAIXO")
                    .setHeader("timestamp", LocalDateTime.now().toString())
                    .build();

            log.info("Enviando notificação de estoque baixo para produto ID: {}", produtoId);
            
            queueMessagingTemplate.send(notificacaoEstoqueQueue, notificacao);
            
        } catch (Exception e) {
            log.error("Erro ao enviar notificação de estoque baixo para produto ID: {}, Erro: {}", 
                    produtoId, e.getMessage(), e);
        }
    }

    /**
     * Envia mensagem de auditoria para log assíncrono.
     *
     * @param operacao Tipo de operação realizada
     * @param entidade Entidade afetada
     * @param entidadeId ID da entidade
     * @param usuarioId ID do usuário
     * @param detalhes Detalhes da operação
     */
    public void enviarAuditoria(String operacao, String entidade, Long entidadeId, 
                               String usuarioId, String detalhes) {
        try {
            var auditoria = MessageBuilder.withPayload(detalhes)
                    .setHeader("operacao", operacao)
                    .setHeader("entidade", entidade)
                    .setHeader("entidadeId", entidadeId)
                    .setHeader("usuarioId", usuarioId)
                    .setHeader("timestamp", LocalDateTime.now().toString())
                    .build();

            log.debug("Enviando mensagem de auditoria: {} - {} ID: {}", operacao, entidade, entidadeId);
            
            queueMessagingTemplate.send(auditoriaQueue, auditoria);
            
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem de auditoria. Operação: {}, Entidade: {}, ID: {}, Erro: {}", 
                    operacao, entidade, entidadeId, e.getMessage(), e);
        }
    }
} 