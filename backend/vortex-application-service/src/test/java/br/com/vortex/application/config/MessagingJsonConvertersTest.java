package br.com.vortex.application.config;

import br.com.vortex.application.dto.AlertaEstoqueEventDTO;
import br.com.vortex.application.dto.MovimentoEstoqueEventDTO;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Conversores JSON (Jackson 3) de Kafka e RabbitMQ, com a configuração real de {@link KafkaConfig} e
 * {@link RabbitMQConfig}, sem broker: os eventos polimórficos ({@code @JsonTypeInfo} em
 * {@code KafkaEventDTO}) precisam voltar como o subtipo certo.
 */
class MessagingJsonConvertersTest {

    private static final String TOPIC = "vortex.movimento.estoque";

    @Test
    void kafkaConfigUsesJackson3SerializerAndDeserializer() {
        KafkaConfig config = kafkaConfig();

        assertEquals(JacksonJsonSerializer.class,
                config.producerFactory().getConfigurationProperties().get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals(JacksonJsonDeserializer.class,
                config.consumerFactory().getConfigurationProperties().get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    }

    @Test
    void kafkaRoundTripWithTypeHeaders() {
        KafkaConfig config = kafkaConfig();
        MovimentoEstoqueEventDTO event = movimentoEvent();

        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>();
             JacksonJsonDeserializer<Object> deserializer = new JacksonJsonDeserializer<>()) {
            serializer.configure(config.producerFactory().getConfigurationProperties(), false);
            deserializer.configure(config.consumerFactory().getConfigurationProperties(), false);

            RecordHeaders headers = new RecordHeaders();
            byte[] data = serializer.serialize(TOPIC, headers, event);
            Object received = deserializer.deserialize(TOPIC, headers, data);

            assertInstanceOf(MovimentoEstoqueEventDTO.class, received);
            assertEquals(event, received);
        }
    }

    @Test
    void kafkaRoundTripWithoutTypeHeadersUsesDefaultTypeAndEventType() {
        // Produtor sem cabeçalhos de tipo: vale o VALUE_DEFAULT_TYPE (KafkaEventDTO) e o "eventType" do JSON
        KafkaConfig config = kafkaConfig();
        MovimentoEstoqueEventDTO event = movimentoEvent();

        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>();
             JacksonJsonDeserializer<Object> deserializer = new JacksonJsonDeserializer<>()) {
            serializer.configure(Map.of("spring.json.add.type.headers", false), false);
            deserializer.configure(config.consumerFactory().getConfigurationProperties(), false);

            byte[] data = serializer.serialize(TOPIC, new RecordHeaders(), event);
            Object received = deserializer.deserialize(TOPIC, new RecordHeaders(), data);

            assertInstanceOf(MovimentoEstoqueEventDTO.class, received);
            assertEquals(event, received);
        }
    }

    @Test
    void rabbitConverterRoundTripsEvent() {
        MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();
        AlertaEstoqueEventDTO event = new AlertaEstoqueEventDTO();
        event.setTipoAlerta("ESTOQUE_BAIXO");
        event.setProdutoId(3L);
        event.setQuantidadeAtual(4);
        event.setQuantidadeMinima(10);
        event.setAcaoImediata(Boolean.TRUE);
        event.setUserId("42");

        Message message = converter.toMessage(event, new MessageProperties());
        Object received = converter.fromMessage(message);

        assertEquals(MessageProperties.CONTENT_TYPE_JSON, message.getMessageProperties().getContentType());
        assertInstanceOf(AlertaEstoqueEventDTO.class, received);
        assertEquals(event, received);
    }

    @Test
    void rabbitConverterStillReadsJdkMapsSentByTheQueueTestEndpoint() {
        // QueueMonitoringService publica HashMap e lê de volta com receiveAndConvert (sem tipo inferido)
        MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();
        Map<String, Object> payload = new HashMap<>(Map.of("_test", true, "mensagem", "ok"));

        Object received = converter.fromMessage(converter.toMessage(payload, new MessageProperties()));

        assertEquals(payload, received);
    }

    @Test
    void rabbitConverterRejectsTypeHeaderOutsideTrustedPackages() {
        MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", NotTrusted.class.getName());
        Message message = new Message("{\"value\":\"x\"}".getBytes(StandardCharsets.UTF_8), properties);

        assertThrows(RuntimeException.class, () -> converter.fromMessage(message));
    }

    public static class NotTrusted {
        public String value;
    }

    private static KafkaConfig kafkaConfig() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "vortex-test-group");
        return config;
    }

    private static MovimentoEstoqueEventDTO movimentoEvent() {
        MovimentoEstoqueEventDTO event = new MovimentoEstoqueEventDTO();
        event.setMovimentoId(21L);
        event.setProdutoId(1L);
        event.setProdutoDescricao("Smartphone Samsung Galaxy S21");
        event.setTipoMovimentacao(TipoMovimentacao.SAIDA);
        event.setQuantidadeMovimentada(2);
        event.setValorVenda(new BigDecimal("2025.00"));
        event.setValorFornecedor(new BigDecimal("1500.00"));
        event.setEstoqueAnterior(10);
        event.setEstoqueAtual(8);
        event.setDataMovimento(LocalDateTime.of(2026, 11, 7, 14, 30, 15, 123_456_000));
        event.setUserId("42");
        return event;
    }
}
