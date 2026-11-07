package br.com.vortex.application.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.function.Predicate;

/**
 * Descarta as auto-configurações das integrações opcionais quando a flag delas está desligada.
 *
 * <p>Os starters de Kafka, RabbitMQ, Redis e Spring Cloud AWS ficam sempre no classpath, e as
 * auto-configurações deles criariam clientes, pools e templates em qualquer perfil (inclusive no
 * {@code dev}), mesmo sem uso. As flags abaixo já governam as classes de configuração da aplicação
 * ({@code KafkaConfig}, {@code RabbitMQConfig}, {@code CacheConfig}, {@code SqsConfig}); este filtro
 * aplica a mesma regra às auto-configurações do Boot e do Spring Cloud AWS:
 * <ul>
 *   <li>{@code kafka.enabled=true}: Kafka</li>
 *   <li>{@code rabbitmq.enabled=true}: RabbitMQ</li>
 *   <li>{@code spring.cache.type=redis}: Redis (Spring Data Redis)</li>
 *   <li>{@code sqs.enabled=true}: AWS (SQS é a única integração AWS da aplicação)</li>
 * </ul>
 *
 * <p>Ao contrário de {@code spring.autoconfigure.exclude}, que é uma lista única e é sobrescrita
 * quando dois perfis a definem, o filtro vale igual em qualquer combinação de perfis. Registrado em
 * {@code META-INF/spring.factories}.
 */
public class OptionalIntegrationsAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final List<Gate> GATES = List.of(
            new Gate("org.springframework.boot.kafka.autoconfigure.", env -> isTrue(env, "kafka.enabled")),
            new Gate("org.springframework.boot.amqp.autoconfigure.", env -> isTrue(env, "rabbitmq.enabled")),
            new Gate("org.springframework.boot.data.redis.autoconfigure.",
                    env -> "redis".equalsIgnoreCase(env.getProperty("spring.cache.type", ""))),
            new Gate("io.awspring.cloud.autoconfigure.", env -> isTrue(env, "sqs.enabled")));

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            matches[i] = isEnabled(autoConfigurationClasses[i]);
        }
        return matches;
    }

    boolean isEnabled(String autoConfigurationClass) {
        if (autoConfigurationClass == null || environment == null) {
            return true;
        }
        for (Gate gate : GATES) {
            if (autoConfigurationClass.startsWith(gate.packagePrefix())) {
                return gate.enabled().test(environment);
            }
        }
        return true;
    }

    private static boolean isTrue(Environment environment, String key) {
        return environment.getProperty(key, Boolean.class, false);
    }

    private record Gate(String packagePrefix, Predicate<Environment> enabled) {
    }
}
