package br.com.vortex.authorization.service;

import br.com.vortex.authorization.config.MessagingConfig;
import br.com.vortex.authorization.event.UserLoggedOutEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Teste unitário (sem Quarkus) do flag auth.event.enabled, com um Emitter falso: ligado, o evento
 * vai para o canal; desligado, o canal nem é tocado. O caminho pelo CDI está no EventPublisherMessagingTest.
 */
class EventPublisherTest {

    private Emitter<String> emitter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emitter = mock(Emitter.class);
        when(emitter.send(anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("Enabled: publishEvent sends the event JSON to user-events")
    void publishesWhenEnabled() {
        publisher(true).publishEvent(event());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(emitter).send(payload.capture());
        assertTrue(payload.getValue().contains("\"eventType\":\"user.logged_out\""), payload.getValue());
        assertTrue(payload.getValue().contains("\"userEmail\":\"user@test.com\""), payload.getValue());
    }

    @Test
    @DisplayName("Disabled: publishEvent does not touch the channel")
    void publishesNothingWhenDisabled() {
        publisher(false).publishEvent(event());

        verifyNoInteractions(emitter);
    }

    @Test
    @DisplayName("Enabled: publishEventSync sends and waits for the acknowledgement")
    void publishesSyncWhenEnabled() {
        publisher(true).publishEventSync(event());

        verify(emitter).send(anyString());
    }

    @Test
    @DisplayName("Disabled: publishEventSync does not touch the channel")
    void publishesNothingSyncWhenDisabled() {
        publisher(false).publishEventSync(event());

        verifyNoInteractions(emitter);
    }

    private EventPublisher publisher(boolean enabled) {
        EventPublisher publisher = new EventPublisher();
        publisher.objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher.messagingProperties = new MessagingConfig.MessagingProperties(enabled, "auth");
        UserEventsChannel channel = new UserEventsChannel();
        channel.emitter = emitter;
        publisher.userEventsChannel = channel;
        return publisher;
    }

    private static UserLoggedOutEvent event() {
        return new UserLoggedOutEvent(UUID.randomUUID(), "user@test.com", "user",
            "session-1", "user_initiated", "127.0.0.1", "JUnit");
    }
}
