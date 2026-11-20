package br.com.vortex.application.service;

import br.com.vortex.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável por consumir eventos do Apache Kafka.
 *
 * Recebe movimentos de estoque, eventos de produto, alertas de estoque e auditoria; o processamento de cada
 * um mora no {@link EventosNegocioHandler}, o mesmo do RabbitMQ. Aqui fica só o acknowledge: sem ele, em
 * caso de erro, a mensagem é reprocessada.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final EventosNegocioHandler handler;

    @KafkaListener(topics = "vortex.movimento.estoque", groupId = "vortex-movimento-group")
    public void processarMovimentoEstoque(@Payload MovimentoEstoqueEventDTO event,
                                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset,
                                        Acknowledgment acknowledgment) {
        try {
            log.info("Processando evento de movimento de estoque - Produto: {}, Tipo: {}, Quantidade: {}",
                event.getProdutoId(), event.getTipoMovimentacao(), event.getQuantidadeMovimentada());
            handler.movimentoEstoque(event);
            acknowledgment.acknowledge();
            log.debug("Evento de movimento processado com sucesso - Key: {}, Offset: {}", key, offset);
        } catch (Exception e) {
            log.error("Erro ao processar evento de movimento de estoque - Key: {}, Offset: {}", key, offset, e);
        }
    }

    @KafkaListener(topics = "vortex.produto.events", groupId = "vortex-produto-group")
    public void processarEventoProduto(@Payload ProdutoEventDTO event,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                     Acknowledgment acknowledgment) {
        try {
            log.info("Processando evento de produto - Ação: {}, Produto: {}",
                event.getAction(), event.getProdutoId());
            handler.eventoProduto(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Erro ao processar evento de produto - Key: {}", key, e);
        }
    }

    @KafkaListener(topics = "vortex.alertas.estoque", groupId = "vortex-alertas-group")
    public void processarAlertaEstoque(@Payload AlertaEstoqueEventDTO event,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                     Acknowledgment acknowledgment) {
        try {
            log.warn("Processando alerta de estoque - Tipo: {}, Produto: {}, Quantidade: {}",
                event.getTipoAlerta(), event.getProdutoId(), event.getQuantidadeAtual());
            handler.alertaEstoque(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Erro ao processar alerta de estoque - Key: {}", key, e);
        }
    }

    @KafkaListener(topics = "vortex.auditoria", groupId = "vortex-auditoria-group")
    public void processarAuditoria(@Payload AuditoriaEventDTO event,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                 Acknowledgment acknowledgment) {
        try {
            log.debug("Processando evento de auditoria - Ação: {}, Entidade: {}",
                event.getAcao(), event.getEntidade());
            handler.auditoria(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Erro ao processar evento de auditoria - Key: {}", key, e);
        }
    }
}
