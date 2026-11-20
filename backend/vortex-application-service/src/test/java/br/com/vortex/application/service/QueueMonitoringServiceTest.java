package br.com.vortex.application.service;

import br.com.vortex.application.dto.QueueInfoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Respostas do monitoramento de filas (status, listagem, leitura e publicação de teste) com o RabbitTemplate
 * simulado, sem broker.
 */
class QueueMonitoringServiceTest {

    private static final String EXCHANGE = "vortex.exchange";
    private static final String FILA = "vortex.auditoria.queue";

    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void systemStatusReportsAConnectedRabbitMqAndAConfiguredKafka() {
        QueueMonitoringService service = service(true, true);

        Map<String, Object> status = (Map<String, Object>) service.obterStatusSistema();

        assertEquals("CONNECTED", status.get("rabbitmqStatus"));
        assertEquals("CONFIGURED", status.get("kafkaStatus"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void systemStatusReportsADisconnectedRabbitMqWithTheError() {
        doThrow(new IllegalStateException("broker fora"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        QueueMonitoringService service = service(true, false);

        Map<String, Object> status = (Map<String, Object>) service.obterStatusSistema();

        assertEquals("DISCONNECTED", status.get("rabbitmqStatus"));
        assertEquals("broker fora", status.get("rabbitmqError"));
        assertEquals("DISABLED", status.get("kafkaStatus"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void systemStatusReportsRabbitMqDisabledWithoutTheTemplate() {
        QueueMonitoringService service = serviceWithoutRabbit(false);

        Map<String, Object> status = (Map<String, Object>) service.obterStatusSistema();

        assertEquals("DISABLED", status.get("rabbitmqStatus"));
    }

    @Test
    void listsRabbitMqQueuesAndKafkaTopicsAsActive() {
        List<QueueInfoDTO> filas = service(true, true).listarFilas();

        assertEquals(10, filas.size());
        assertTrue(filas.stream().allMatch(fila -> "ACTIVE".equals(fila.getStatus())));
        assertEquals(5, filas.stream().filter(fila -> "RabbitMQ".equals(fila.getTipo())).count());
        assertEquals(5, filas.stream().filter(fila -> "Kafka".equals(fila.getTipo())).count());
    }

    @Test
    void queueInfoIsActiveForRabbitMqAndForKafka() {
        QueueInfoDTO rabbit = service(true, false).obterInformacoesFila(FILA);
        QueueInfoDTO kafka = serviceWithoutRabbit(true).obterInformacoesFila("vortex.auditoria");

        assertEquals("ACTIVE", rabbit.getStatus());
        assertEquals("RabbitMQ", rabbit.getTipo());
        assertEquals(EXCHANGE, rabbit.getExchange());
        assertEquals("ACTIVE", kafka.getStatus());
        assertEquals("Kafka", kafka.getTipo());
    }

    @Test
    void readingMessagesWithoutRabbitMqAnswersBrokerDisabled() {
        Map<String, Object> resultado = serviceWithoutRabbit(false).buscarMensagens(FILA, 5);

        assertEquals("BROKER_DISABLED", resultado.get("status"));
        assertEquals("RabbitMQ não está habilitado ou disponível", resultado.get("mensagem"));
        assertEquals(List.of(), resultado.get("mensagens"));
    }

    @Test
    void readingMessagesReturnsWhatTheQueueHad() {
        when(rabbitTemplate.receiveAndConvert(anyString(), anyLong())).thenReturn("primeira", "segunda", null);

        Map<String, Object> resultado = service(true, false).buscarMensagens(FILA, 5);

        assertEquals("SUCCESS", resultado.get("status"));
        assertEquals("Encontradas 2 mensagens", resultado.get("mensagem"));
        assertEquals(2, ((List<?>) resultado.get("mensagens")).size());
        assertEquals(2, resultado.get("totalEncontradas"));
    }

    @Test
    void readingAnEmptyQueueAnswersQueueEmpty() {
        Map<String, Object> resultado = service(true, false).buscarMensagens(FILA, 5);

        assertEquals("QUEUE_EMPTY", resultado.get("status"));
        assertEquals("Queue is empty", resultado.get("mensagem"));
        assertEquals(List.of(), resultado.get("mensagens"));
    }

    @Test
    void readingMessagesReportsTheBrokerError() {
        when(rabbitTemplate.receiveAndConvert(anyString(), anyLong())).thenThrow(new IllegalStateException("canal fechado"));

        Map<String, Object> resultado = service(true, false).buscarMensagens(FILA, 5);

        assertEquals("ERROR", resultado.get("status"));
        assertEquals("Erro ao buscar mensagens: canal fechado", resultado.get("mensagem"));
        assertEquals(List.of(), resultado.get("mensagens"));
        assertEquals(0, resultado.get("totalEncontradas"));
    }

    @Test
    void publishingWithoutRabbitMqAnswersBrokerDisabled() {
        Map<String, Object> resultado = serviceWithoutRabbit(false).publicarMensagemTeste(FILA, Map.of("a", 1));

        assertEquals("BROKER_DISABLED", resultado.get("status"));
        assertEquals("RabbitMQ não está habilitado ou disponível", resultado.get("mensagem"));
    }

    @Test
    void publishingToAKnownQueueUsesItsTestRoutingKey() {
        Map<String, Object> resultado = service(true, false).publicarMensagemTeste(FILA, Map.of("a", 1));

        assertEquals("SUCCESS", resultado.get("status"));
        assertEquals("Mensagem de teste publicada com sucesso", resultado.get("mensagem"));
        assertEquals("vortex.auditoria.test", resultado.get("routingKey"));
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("vortex.auditoria.test"), any(Object.class));
    }

    @Test
    void publishingToAnUnknownQueueGoesStraightToTheQueue() {
        Map<String, Object> resultado = service(true, false).publicarMensagemTeste("outra.fila", Map.of("a", 1));

        assertEquals("SUCCESS", resultado.get("status"));
        assertEquals("Mensagem de teste publicada diretamente na fila", resultado.get("mensagem"));
        assertEquals("DIRECT_TO_QUEUE", resultado.get("routingKey"));
        verify(rabbitTemplate).convertAndSend(eq("outra.fila"), any(Object.class));
    }

    @Test
    void publishingReportsTheBrokerError() {
        doThrow(new IllegalStateException("canal fechado"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        Map<String, Object> resultado = service(true, false).publicarMensagemTeste(FILA, Map.of("a", 1));

        assertEquals("ERROR", resultado.get("status"));
        assertEquals("Erro ao publicar mensagem: canal fechado", resultado.get("mensagem"));
    }

    private QueueMonitoringService service(boolean rabbitmqEnabled, boolean kafkaEnabled) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("rabbitTemplate", rabbitTemplate);
        return configure(new QueueMonitoringService(beans.getBeanProvider(RabbitTemplate.class)),
                rabbitmqEnabled, kafkaEnabled);
    }

    private static QueueMonitoringService serviceWithoutRabbit(boolean kafkaEnabled) {
        QueueMonitoringService service =
                new QueueMonitoringService(new StaticListableBeanFactory().getBeanProvider(RabbitTemplate.class));
        return configure(service, false, kafkaEnabled);
    }

    private static QueueMonitoringService configure(QueueMonitoringService service, boolean rabbitmqEnabled,
                                                    boolean kafkaEnabled) {
        ReflectionTestUtils.setField(service, "brokerType", "rabbitmq");
        ReflectionTestUtils.setField(service, "rabbitmqEnabled", rabbitmqEnabled);
        ReflectionTestUtils.setField(service, "kafkaEnabled", kafkaEnabled);
        ReflectionTestUtils.setField(service, "exchangeName", EXCHANGE);
        return service;
    }
}
