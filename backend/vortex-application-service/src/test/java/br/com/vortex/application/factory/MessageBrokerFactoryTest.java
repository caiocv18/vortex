package br.com.vortex.application.factory;

import br.com.vortex.application.service.KafkaProducerService;
import br.com.vortex.application.service.MessageBrokerService;
import br.com.vortex.application.service.RabbitMQProducerService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * A escolha do broker. Sem a flag do broker escolhido, ou sem o bean dele (o que acontece sem perfil de
 * mensageria, porque os producers são {@code @ConditionalOnProperty}), o serviço é o NoOp: nada publica e
 * {@code isAvailable()} é falso, então o MovimentoEstoqueService nem monta os eventos. O caso de ponta a ponta
 * no perfil {@code test} é o {@code OptionalIntegrationsGatingTest}.
 */
class MessageBrokerFactoryTest {

    @ParameterizedTest(name = "tipo={0}, kafka={1}, rabbitmq={2}, beans={3} → NoOp")
    @CsvSource({
            "kafka,    false, false, false",
            "kafka,    false, false, true",
            "kafka,    true,  false, false",
            "rabbitmq, false, false, true",
            "rabbitmq, false, true,  false",
            "none,     true,  true,  true",
            "outro,    false, false, false",
    })
    void fallsBackToNoOp(String type, boolean kafkaEnabled, boolean rabbitmqEnabled, boolean withBeans) {
        MessageBrokerService service = factory(type, kafkaEnabled, rabbitmqEnabled, withBeans).messageBrokerService();

        assertEquals("NoOp", service.getType());
        assertFalse(service.isAvailable());
    }

    @ParameterizedTest(name = "tipo={0} com flag e bean → o producer dele")
    @CsvSource({"kafka", "KAFKA", "rabbitmq"})
    void picksTheEnabledProducer(String type) {
        MessageBrokerFactory factory = factory(type, true, true, true);

        Object expected = type.equalsIgnoreCase("kafka")
                ? ReflectionTestUtils.getField(factory, "kafkaProducerService")
                : ReflectionTestUtils.getField(factory, "rabbitMQProducerService");
        assertSame(expected, factory.messageBrokerService());
    }

    private static MessageBrokerFactory factory(String type, boolean kafkaEnabled, boolean rabbitmqEnabled,
                                                boolean withBeans) {
        MessageBrokerFactory factory = new MessageBrokerFactory();
        ReflectionTestUtils.setField(factory, "brokerType", type);
        ReflectionTestUtils.setField(factory, "kafkaEnabled", kafkaEnabled);
        ReflectionTestUtils.setField(factory, "rabbitmqEnabled", rabbitmqEnabled);
        if (withBeans) {
            ReflectionTestUtils.setField(factory, "kafkaProducerService", mock(KafkaProducerService.class));
            ReflectionTestUtils.setField(factory, "rabbitMQProducerService", mock(RabbitMQProducerService.class));
        }
        return factory;
    }
}
