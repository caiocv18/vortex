package br.com.vortex.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class OptionalIntegrationsAutoConfigurationFilterTest {

    private static final String[] CANDIDATES = {
            "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
            "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
            "io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration",
            "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration"
    };

    @Test
    void dropsEveryOptionalIntegrationWhenFlagsAreOff() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.cache.type", "simple");

        assertArrayEquals(new boolean[] {false, false, false, false, true}, match(environment));
    }

    @Test
    void keepsEachIntegrationWhenItsFlagIsOn() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("kafka.enabled", "true")
                .withProperty("rabbitmq.enabled", "true")
                .withProperty("spring.cache.type", "redis")
                .withProperty("sqs.enabled", "true");

        assertArrayEquals(new boolean[] {true, true, true, true, true}, match(environment));
    }

    @Test
    void flagsAreIndependent() {
        MockEnvironment environment = new MockEnvironment().withProperty("kafka.enabled", "true");

        assertArrayEquals(new boolean[] {true, false, false, false, true}, match(environment));
    }

    private static boolean[] match(MockEnvironment environment) {
        OptionalIntegrationsAutoConfigurationFilter filter = new OptionalIntegrationsAutoConfigurationFilter();
        filter.setEnvironment(environment);
        return filter.match(CANDIDATES, null);
    }
}
