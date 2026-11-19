package br.com.vortex.authorization;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * O perfil kafka como o launcher o ativa no auth (start-vortex.sh -m kafka roda com o perfil kafka), com o
 * conector do canal user-events trocado pelo em memória, para não precisar de broker.
 *
 * <p>Na suíte, o application.properties de src/test/resources encobre o principal inteiro (vem antes no
 * classpath), então nenhuma linha %kafka do jar chega ao Quarkus. Por isso a linha que liga os eventos no perfil
 * kafka é lida do application.properties principal, o mesmo que vai no jar, e repassada sem mexer no valor: o
 * Quarkus resolve o {@code ${AUTH_EVENT_ENABLED:true}} dele. Apagar ou desligar essa linha reprova o teste.
 *
 * <p>"test" vem por último: com vários perfis ativos o último vence, então banco, chaves e demais valores
 * continuam os de teste.
 */
public class KafkaProfileEventsTestProfile implements QuarkusTestProfile {

    public static final String EVENTS_IN_KAFKA_PROFILE = "%kafka.auth.event.enabled";
    private static final Path MAIN_PROPERTIES = Path.of("src", "main", "resources", "application.properties");

    @Override
    public String getConfigProfile() {
        return "kafka,test";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Com o prefixo do perfil, para ganhar do %kafka...=smallrye-kafka mesmo se o principal for lido
            "%kafka.mp.messaging.outgoing.user-events.connector", "smallrye-in-memory",
            EVENTS_IN_KAFKA_PROFILE, mainProperty(EVENTS_IN_KAFKA_PROFILE)
        );
    }

    private static String mainProperty(String key) {
        Properties file = new Properties();
        try (Reader reader = Files.newBufferedReader(MAIN_PROPERTIES, StandardCharsets.UTF_8)) {
            file.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String value = file.getProperty(key);
        if (value == null) {
            throw new IllegalStateException(key + " sumiu de " + MAIN_PROPERTIES);
        }
        return value;
    }
}
