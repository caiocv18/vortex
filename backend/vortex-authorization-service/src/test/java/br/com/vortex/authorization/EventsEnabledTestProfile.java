package br.com.vortex.authorization;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Liga os eventos (auth.event.enabled=true) e manda o canal user-events para o conector em memória,
 * sem Kafka nem RabbitMQ. O perfil de teste padrão continua com os eventos desligados e sem conector.
 *
 * O build é também "dev", como no quarkus:dev com mensageria (start-vortex.sh --modo dev -m kafka roda
 * com -Dquarkus.profile=dev,kafka): o AuthEventConsumer (@IfBuildProfile("dev")) fica ativo e o canal
 * tem dois consumidores, ele e o conector. "test" vem por último de propósito: com vários perfis ativos
 * o último vence, então banco, chaves e demais valores continuam os de teste.
 */
public class EventsEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "dev,test";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "auth.event.enabled", "true",
            "mp.messaging.outgoing.user-events.connector", "smallrye-in-memory"
        );
    }
}
