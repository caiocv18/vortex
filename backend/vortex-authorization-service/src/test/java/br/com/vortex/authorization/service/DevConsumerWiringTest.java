package br.com.vortex.authorization.service;

import br.com.vortex.authorization.EventsEnabledTestProfile;
import br.com.vortex.authorization.event.UserLoggedOutEvent;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Canal user-events com dois consumidores, como no quarkus:dev com -m kafka ou -m rabbitmq: o
 * AuthEventConsumer (só no build dev, ver EventsEnabledTestProfile) e o conector de saída. O Emitter sem
 * @Broadcast aceita um consumidor só, e a subida falhava com TooManyDownstreamCandidatesException; chegar
 * ao corpo do teste já prova o grafo. Depois, um evento publicado chega aos dois.
 */
@QuarkusTest
@TestProfile(EventsEnabledTestProfile.class)
class DevConsumerWiringTest {

    @Inject
    EventPublisher eventPublisher;

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Test
    @DisplayName("Dev build with a connector: one event reaches both the dev consumer and the outgoing connector")
    void devConsumerAndConnectorBothReceiveTheEvent() throws InterruptedException {
        assertTrue(Arc.container().instance(AuthEventConsumer.class).isAvailable(),
            "neste perfil o consumidor de dev precisa estar ativo, senão o teste não prova nada");

        InMemorySink<String> sink = connector.sink("user-events");
        sink.clear();
        List<String> consumerLogs = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                consumerLogs.add(String.valueOf(logRecord.getMessage()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger consumerLogger = Logger.getLogger(AuthEventConsumer.class.getName());
        consumerLogger.addHandler(handler);
        try {
            eventPublisher.publishEvent(new UserLoggedOutEvent(UUID.randomUUID(), "dev@test.com", "dev",
                "session-dev", "user_initiated", "127.0.0.1", "JUnit"));

            long deadline = System.currentTimeMillis() + 5_000;
            while ((sink.received().isEmpty() || consumerLogs.stream().noneMatch(m -> m.startsWith("User logged out")))
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
        } finally {
            consumerLogger.removeHandler(handler);
        }

        assertEquals(1, sink.received().size(), "o conector de saída precisa receber o evento");
        assertTrue(sink.received().get(0).getPayload().contains("\"eventType\":\"user.logged_out\""));
        assertTrue(consumerLogs.stream().anyMatch(m -> m.startsWith("User logged out")),
            "o AuthEventConsumer precisa tratar o mesmo evento; logs vistos: " + consumerLogs);
    }
}
