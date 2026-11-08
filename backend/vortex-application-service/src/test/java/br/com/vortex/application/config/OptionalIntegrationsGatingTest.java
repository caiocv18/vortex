package br.com.vortex.application.config;

import br.com.vortex.application.service.KafkaProducerService;
import br.com.vortex.application.service.MessageBrokerService;
import br.com.vortex.application.service.RabbitMQProducerService;
import br.com.vortex.application.service.SqsConsumerService;
import br.com.vortex.application.service.SqsProducerService;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Com as flags desligadas (perfil test, como o dev), nenhum bean de Kafka, RabbitMQ, Redis, SQS ou
 * AWS pode existir: nem os da aplicação nem os das auto-configurações.
 */
@SpringBootTest
@ActiveProfiles("test")
class OptionalIntegrationsGatingTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MessageBrokerService messageBrokerService;

    @Test
    void noMessagingCacheOrAwsBeansWhenFlagsAreOff() {
        List<Class<?>> absent = List.of(
                KafkaTemplate.class, KafkaAdmin.class, KafkaProducerService.class,
                RabbitTemplate.class, ConnectionFactory.class, RabbitMQProducerService.class,
                RedisConnectionFactory.class, CacheConfig.class,
                SqsAsyncClient.class, SqsTemplate.class, AwsCredentialsProvider.class,
                SqsConfig.class, SqsProducerService.class, SqsConsumerService.class);

        for (Class<?> type : absent) {
            assertEquals(0, context.getBeanNamesForType(type).length, () -> "bean inesperado: " + type.getName());
        }
    }

    @Test
    void messageBrokerFallsBackToNoOp() {
        assertEquals("NoOp", messageBrokerService.getType());
        assertFalse(messageBrokerService.isAvailable());
    }
}
