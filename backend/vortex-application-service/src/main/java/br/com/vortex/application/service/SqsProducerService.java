package br.com.vortex.application.service;

import br.com.vortex.application.dto.MovimentoEstoqueMessageDTO;
import io.awspring.cloud.sqs.operations.SqsOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço responsável por enviar mensagens para filas Amazon SQS.
 * Centraliza a lógica de produção de mensagens para diferentes tipos de operações.
 * Registrado por {@code SqsConfig}, só com {@code sqs.enabled=true}.
 */
@Slf4j
public class SqsProducerService {

    private final SqsOperations sqsOperations;

    @Value("${sqs.queue.movimento-estoque}")
    private String movimentoEstoqueQueue;

    @Value("${sqs.queue.notificacao-estoque}")
    private String notificacaoEstoqueQueue;

    @Value("${sqs.queue.auditoria}")
    private String auditoriaQueue;

    public SqsProducerService(SqsOperations sqsOperations) {
        this.sqsOperations = sqsOperations;
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

            sqsOperations.send(to -> to.queue(movimentoEstoqueQueue).payload(message));

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
            String notificacao = String.format("Estoque baixo - Produto ID: %d, Quantidade atual: %d, Mínima: %d",
                    produtoId, quantidadeAtual, quantidadeMinima);
            Map<String, Object> headers = new HashMap<>();
            putIfNotNull(headers, "produtoId", produtoId);
            headers.put("tipo", "ESTOQUE_BAIXO");
            headers.put("timestamp", LocalDateTime.now().toString());

            log.info("Enviando notificação de estoque baixo para produto ID: {}", produtoId);

            sqsOperations.send(to -> to.queue(notificacaoEstoqueQueue).payload(notificacao).headers(headers));

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
            // Atributos de mensagem SQS não aceitam valor nulo: cabeçalho nulo é omitido
            Map<String, Object> headers = new HashMap<>();
            putIfNotNull(headers, "operacao", operacao);
            putIfNotNull(headers, "entidade", entidade);
            putIfNotNull(headers, "entidadeId", entidadeId);
            putIfNotNull(headers, "usuarioId", usuarioId);
            headers.put("timestamp", LocalDateTime.now().toString());

            log.debug("Enviando mensagem de auditoria: {} - {} ID: {}", operacao, entidade, entidadeId);

            sqsOperations.send(to -> to.queue(auditoriaQueue).payload(detalhes).headers(headers));

        } catch (Exception e) {
            log.error("Erro ao enviar mensagem de auditoria. Operação: {}, Entidade: {}, ID: {}, Erro: {}",
                    operacao, entidade, entidadeId, e.getMessage(), e);
        }
    }

    private static void putIfNotNull(Map<String, Object> headers, String name, Object value) {
        if (value != null) {
            headers.put(name, value);
        }
    }
}
