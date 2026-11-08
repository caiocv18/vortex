package br.com.vortex.authorization.service;

import br.com.vortex.authorization.EventsEnabledTestProfile;
import br.com.vortex.authorization.config.MessagingConfig;
import br.com.vortex.authorization.dto.RegisterRequest;
import br.com.vortex.authorization.entity.RefreshToken;
import br.com.vortex.authorization.event.UserLoggedOutEvent;
import br.com.vortex.authorization.util.TestDataBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
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
    ObjectMapper objectMapper;

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

    @Test
    @DisplayName("Inside a transaction the event leaves only after the commit")
    void eventLeavesOnlyAfterTheCommit() throws InterruptedException {
        QuarkusTransaction.requiringNew().run(() -> {
            eventPublisher.publishEvent(event());
            assertEquals(0, sink.received().size(), "nada pode sair antes do commit");
        });

        awaitMessages(1);
    }

    @Test
    @DisplayName("A transaction that rolls back never emits its event")
    void rolledBackTransactionEmitsNothing() throws InterruptedException {
        // Marcada para rollback depois de publicar (o evento já estava agendado para o commit). O
        // QuarkusTransaction tenta o commit no fim e recebe RollbackException: é o rollback acontecendo.
        assertThrows(QuarkusTransactionException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            eventPublisher.publishEvent(event());
            QuarkusTransaction.setRollbackOnly();
        }));
        // Já marcada para rollback quando publica
        assertThrows(QuarkusTransactionException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            QuarkusTransaction.setRollbackOnly();
            eventPublisher.publishEvent(event());
        }));

        Thread.sleep(300);
        assertEquals(0, sink.received().size());
    }

    @Test
    @DisplayName("Register and login events carry no refresh token and the user's final state")
    void registerAndLoginEventsCarryNoSecret() throws Exception {
        RegisterRequest user = TestDataBuilder.createValidRegisterRequest();
        String registerRefreshToken = given()
            .contentType(ContentType.JSON)
            .body(user)
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(201)
            .extract().path("data.refreshToken");
        String loginRefreshToken = given()
            .contentType(ContentType.JSON)
            .body(TestDataBuilder.createLoginRequest(user.email, user.password))
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(200)
            .extract().path("data.refreshToken");

        awaitMessages(2);
        List<String> payloads = new ArrayList<>();
        for (Message<String> message : sink.received()) {
            payloads.add(message.getPayload());
        }
        for (String payload : payloads) {
            // O refresh token é credencial de 7 dias: quem lesse o tópico ou o log renovava a sessão
            assertFalse(payload.contains(registerRefreshToken), payload);
            assertFalse(payload.contains(loginRefreshToken), payload);
        }

        JsonNode created = eventOfType(payloads, "user.created");
        assertEquals(user.email, created.get("userEmail").asText());
        // register() verifica o usuário automaticamente: o evento sai com esse estado final
        assertTrue(created.get("isVerified").asBoolean(), created.toString());

        JsonNode loggedIn = eventOfType(payloads, "user.logged_in");
        RefreshToken stored = QuarkusTransaction.requiringNew().call(() -> RefreshToken.findByToken(loginRefreshToken));
        // O sessionId identifica a sessão pelo id da linha do refresh token, que não autentica nada
        assertEquals(stored.id.toString(), loggedIn.get("sessionId").asText());
        assertEquals(stored.id.toString(), loggedIn.get("details").get("sessionId").asText());
    }

    private JsonNode eventOfType(List<String> payloads, String eventType) throws Exception {
        for (String payload : payloads) {
            JsonNode node = objectMapper.readTree(payload);
            if (eventType.equals(node.get("eventType").asText())) {
                return node;
            }
        }
        return fail("nenhum evento " + eventType + " em " + payloads);
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
