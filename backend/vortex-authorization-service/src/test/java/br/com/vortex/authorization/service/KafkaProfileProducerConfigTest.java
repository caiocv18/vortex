package br.com.vortex.authorization.service;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * As chaves kafka.* do perfil kafka passam pela validação do próprio kafka-clients, sem broker. Com
 * acks=1 e enable.idempotence=true o ProducerConfig lança "Must set acks to all in order to use the
 * idempotent producer" e o Quarkus não subia no perfil kafka (medido em 07/11/2026, quarkus:dev com
 * -Dquarkus.profile=dev,kafka). Lê o application.properties principal, o mesmo que vai no jar.
 */
class KafkaProfileProducerConfigTest {

    private static final Path MAIN_PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final String KAFKA_DEFAULTS = "%kafka.kafka.";
    private static final String CHANNEL = "%kafka.mp.messaging.outgoing.user-events.";
    // ${VARIAVEL:padrão} vira o padrão, como sem a variável de ambiente
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{[^:}]+:([^}]*)}");

    @Test
    @DisplayName("The kafka profile producer settings are accepted by kafka-clients (idempotence needs acks=all)")
    void kafkaProfileProducerSettingsAreAccepted() throws IOException {
        Properties file = new Properties();
        try (Reader reader = Files.newBufferedReader(MAIN_PROPERTIES, StandardCharsets.UTF_8)) {
            file.load(reader);
        }

        Map<String, Object> producer = new HashMap<>();
        for (String key : file.stringPropertyNames()) {
            if (key.startsWith(KAFKA_DEFAULTS)) {
                producer.put(key.substring(KAFKA_DEFAULTS.length()), resolveDefault(file.getProperty(key)));
            }
        }
        assertTrue(producer.containsKey(ProducerConfig.ACKS_CONFIG), "o perfil kafka precisa fixar acks");
        producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, file.getProperty(CHANNEL + "key.serializer"));
        producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, file.getProperty(CHANNEL + "value.serializer"));

        ProducerConfig config = assertDoesNotThrow(() -> new ProducerConfig(producer));
        assertTrue(config.getBoolean(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        // O kafka-clients normaliza "all" para "-1"
        assertTrue(List.of("all", "-1").contains(config.getString(ProducerConfig.ACKS_CONFIG)),
            "acks efetivo: " + config.getString(ProducerConfig.ACKS_CONFIG));
    }

    private static String resolveDefault(String value) {
        Matcher matcher = EXPRESSION.matcher(value);
        return matcher.matches() ? matcher.group(1) : value;
    }
}
