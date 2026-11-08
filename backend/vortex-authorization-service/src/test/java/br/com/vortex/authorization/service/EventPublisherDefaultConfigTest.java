package br.com.vortex.authorization.service;

import br.com.vortex.authorization.config.MessagingConfig;
import br.com.vortex.authorization.event.UserLoggedOutEvent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Perfil de teste padrão, igual ao padrão de leve e produção: auth.event.enabled=false e o canal
 * user-events sem conector. A aplicação sobe assim (é este app que roda a suíte inteira) e publicar
 * não exige Kafka nem RabbitMQ: o UserEventsChannel nem chega a ser criado.
 */
@QuarkusTest
class EventPublisherDefaultConfigTest {

    @Inject
    EventPublisher eventPublisher;

    @Inject
    MessagingConfig.MessagingProperties messagingProperties;

    @Test
    @DisplayName("Events are off by default: publishing never creates the user-events channel bean")
    void eventsAreOffByDefault() {
        assertFalse(messagingProperties.isEventEnabled());

        UserLoggedOutEvent event = new UserLoggedOutEvent(UUID.randomUUID(), "off@test.com", "off",
            "session-1", "user_initiated", "127.0.0.1", "JUnit");
        eventPublisher.publishEvent(event);
        eventPublisher.publishEventSync(event);

        // O publishEvent engole exceções, então "não lançou" não provaria nada: o que importa é que o
        // canal não foi tocado. Criado, ele tentaria ligar o Emitter e falharia (sem conector).
        InjectableBean<UserEventsChannel> channelBean = Arc.container().instance(UserEventsChannel.class).getBean();
        assertNull(Arc.container().getActiveContext(ApplicationScoped.class).get(channelBean),
            "com auth.event.enabled=false o UserEventsChannel não pode ser instanciado");
    }
}
