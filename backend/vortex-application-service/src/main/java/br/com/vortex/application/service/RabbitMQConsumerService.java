package br.com.vortex.application.service;

import br.com.vortex.application.dto.*;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Serviço responsável por consumir eventos do RabbitMQ.
 *
 * Recebe movimentos de estoque, eventos de produto, alertas de estoque e auditoria; o processamento de cada
 * um mora no {@link EventosNegocioHandler}, o mesmo do Kafka. Aqui fica só a confirmação: ack no sucesso,
 * nack com reenvio para a fila no erro.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RabbitMQConsumerService {

    private final EventosNegocioHandler handler;

    @RabbitListener(queues = "vortex.movimento.estoque.queue")
    public void processarMovimentoEstoque(@Payload MovimentoEstoqueEventDTO event,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                        Channel channel) throws IOException {
        log.info("Processando evento de movimento de estoque via RabbitMQ - Produto: {}, Tipo: {}, Quantidade: {}",
            event.getProdutoId(), event.getTipoMovimentacao(), event.getQuantidadeMovimentada());
        confirmar(() -> handler.movimentoEstoque(event), "movimento de estoque", deliveryTag, routingKey, channel);
    }

    @RabbitListener(queues = "vortex.produto.events.queue")
    public void processarEventoProduto(@Payload ProdutoEventDTO event,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                     @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                     Channel channel) throws IOException {
        log.info("Processando evento de produto via RabbitMQ - Ação: {}, Produto: {}",
            event.getAction(), event.getProdutoId());
        confirmar(() -> handler.eventoProduto(event), "produto", deliveryTag, routingKey, channel);
    }

    @RabbitListener(queues = "vortex.alertas.estoque.queue")
    public void processarAlertaEstoque(@Payload AlertaEstoqueEventDTO event,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                     @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                     Channel channel) throws IOException {
        log.warn("Processando alerta de estoque via RabbitMQ - Tipo: {}, Produto: {}, Quantidade: {}",
            event.getTipoAlerta(), event.getProdutoId(), event.getQuantidadeAtual());
        confirmar(() -> handler.alertaEstoque(event), "alerta de estoque", deliveryTag, routingKey, channel);
    }

    @RabbitListener(queues = "vortex.auditoria.queue")
    public void processarAuditoria(@Payload AuditoriaEventDTO event,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                 @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                 Channel channel) throws IOException {
        log.debug("Processando evento de auditoria via RabbitMQ - Ação: {}, Entidade: {}",
            event.getAcao(), event.getEntidade());
        confirmar(() -> handler.auditoria(event), "auditoria", deliveryTag, routingKey, channel);
    }

    /** Ack no sucesso; no erro, nack com requeue para a mensagem voltar à fila. */
    private static void confirmar(Runnable processamento, String tipo, long deliveryTag, String routingKey,
                                  Channel channel) throws IOException {
        try {
            processamento.run();
            channel.basicAck(deliveryTag, false);
            log.debug("Evento de {} processado com sucesso via RabbitMQ - RoutingKey: {}", tipo, routingKey);
        } catch (Exception e) {
            log.error("Erro ao processar evento de {} via RabbitMQ - RoutingKey: {}", tipo, routingKey, e);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
