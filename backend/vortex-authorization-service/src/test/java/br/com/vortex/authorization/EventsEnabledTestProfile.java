package br.com.vortex.authorization;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Liga os eventos (auth.event.enabled=true) e manda o canal user-events para o conector em memória,
 * sem Kafka nem RabbitMQ. O perfil de teste padrão continua com os eventos desligados e sem conector.
 */
public class EventsEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "auth.event.enabled", "true",
            "mp.messaging.outgoing.user-events.connector", "smallrye-in-memory"
        );
    }
}
