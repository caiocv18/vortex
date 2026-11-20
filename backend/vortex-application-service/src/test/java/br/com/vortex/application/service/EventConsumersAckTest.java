package br.com.vortex.application.service;

import br.com.vortex.application.dto.AlertaEstoqueEventDTO;
import br.com.vortex.application.dto.AuditoriaEventDTO;
import br.com.vortex.application.dto.MovimentoEstoqueEventDTO;
import br.com.vortex.application.dto.ProdutoEventDTO;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Os dois consumidores delegam ao mesmo handler e só cuidam da confirmação: o Kafka confirma no sucesso e
 * deixa sem acknowledge no erro (reprocessa); o RabbitMQ dá ack no sucesso e nack com requeue no erro.
 */
class EventConsumersAckTest {

    private final EventosNegocioHandler handler = mock(EventosNegocioHandler.class);

    @Test
    void kafkaAcknowledgesEveryEventTheHandlerProcessed() {
        KafkaConsumerService consumer = new KafkaConsumerService(handler);
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.processarMovimentoEstoque(new MovimentoEstoqueEventDTO(), "k", "t", 0, 1L, ack);
        consumer.processarEventoProduto(new ProdutoEventDTO(), "k", ack);
        consumer.processarAlertaEstoque(new AlertaEstoqueEventDTO(), "k", ack);
        consumer.processarAuditoria(new AuditoriaEventDTO(), "k", ack);

        verify(ack, times(4)).acknowledge();
    }

    @Test
    void kafkaLeavesAFailedEventUnacknowledged() {
        doThrow(new IllegalStateException("falhou")).when(handler).movimentoEstoque(any());
        doThrow(new IllegalStateException("falhou")).when(handler).eventoProduto(any());
        doThrow(new IllegalStateException("falhou")).when(handler).alertaEstoque(any());
        doThrow(new IllegalStateException("falhou")).when(handler).auditoria(any());
        KafkaConsumerService consumer = new KafkaConsumerService(handler);
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.processarMovimentoEstoque(new MovimentoEstoqueEventDTO(), "k", "t", 0, 1L, ack);
        consumer.processarEventoProduto(new ProdutoEventDTO(), "k", ack);
        consumer.processarAlertaEstoque(new AlertaEstoqueEventDTO(), "k", ack);
        consumer.processarAuditoria(new AuditoriaEventDTO(), "k", ack);

        verify(ack, never()).acknowledge();
    }

    @Test
    void rabbitAcksSuccessAndRequeuesFailure() throws Exception {
        RabbitMQConsumerService consumer = new RabbitMQConsumerService(handler);
        Channel channel = mock(Channel.class);

        consumer.processarMovimentoEstoque(new MovimentoEstoqueEventDTO(), 1L, "rk", channel);
        consumer.processarEventoProduto(new ProdutoEventDTO(), 2L, "rk", channel);
        consumer.processarAlertaEstoque(new AlertaEstoqueEventDTO(), 3L, "rk", channel);
        doThrow(new IllegalStateException("falhou")).when(handler).auditoria(any());
        consumer.processarAuditoria(new AuditoriaEventDTO(), 4L, "rk", channel);

        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
        verify(channel).basicAck(3L, false);
        verify(channel).basicNack(4L, false, true);
        verify(channel, never()).basicAck(4L, false);
    }
}
