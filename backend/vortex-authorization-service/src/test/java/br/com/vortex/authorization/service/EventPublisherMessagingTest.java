package br.com.vortex.authorization.service;

import br.com.vortex.authorization.EventsEnabledTestProfile;
import br.com.vortex.authorization.config.MessagingConfig;
import br.com.vortex.authorization.event.UserLoggedOutEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Eventos ligados pelo CDI de verdade: o flag passa pelo client proxy do MessagingProperties (o que
 * antes sempre lia false) e o evento chega ao canal user-events, aqui no conector em memória.
 */
@QuarkusTest
@TestProfile(EventsEnabledTestProfile.class)
class EventPublisherMessagingTest {

    @Inject
    EventPublisher eventPublisher;

    @Inject
    MessagingConfig.MessagingProperties messagingProperties;

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    private InMemorySink<String> sink;

    @BeforeEach
    void setUp() {
        sink = connector.sink("user-events");
        sink.clear();
    }

    @Test
    @DisplayName("auth.event.enabled=true is seen through the CDI client proxy")
    void flagIsReadThroughTheProxy() {
        assertTrue(messagingProperties.isEventEnabled());
    }

    @Test
    @DisplayName("publishEvent delivers the event JSON to the user-events channel")
    void publishedEventReachesTheChannel() throws InterruptedException {
        eventPublisher.publishEvent(event());

        awaitMessages(1);
        String payload = sink.received().get(0).getPayload();
        assertTrue(payload.contains("\"eventType\":\"user.logged_out\""), payload);
    }

    @Test
    @DisplayName("publishEventSync delivers the event and returns after the acknowledgement")
    void syncPublishIsDelivered() {
        eventPublisher.publishEventSync(event());

        assertEquals(1, sink.received().size());
    }

    // O publishEvent não espera o ack; o conector em memória entrega logo, mas sem garantia de thread
    private void awaitMessages(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (sink.received().size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(expected, sink.received().size());
    }

    private static UserLoggedOutEvent event() {
        return new UserLoggedOutEvent(UUID.randomUUID(), "events@test.com", "events",
            "session-1", "user_initiated", "127.0.0.1", "JUnit");
    }
}
