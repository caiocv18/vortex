package br.com.vortex.authorization.service;

import br.com.vortex.authorization.KafkaProfileEventsTestProfile;
import br.com.vortex.authorization.config.MessagingConfig;
import br.com.vortex.authorization.dto.RegisterRequest;
import br.com.vortex.authorization.util.TestDataBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * No perfil kafka os eventos saem sem AUTH_EVENT_ENABLED: o login publica user.logged_in no canal user-events
 * (tópico vortex-user-events) com o username, que é a chave do join com os movimentos do app no pipeline de
 * dados. O app grava esse mesmo username no userId dos movimentos.
 */
@QuarkusTest
@TestProfile(KafkaProfileEventsTestProfile.class)
class KafkaProfileUserEventsTest {

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
    @DisplayName("The kafka profile turns the auth events on by itself")
    void kafkaProfileEnablesTheEvents() {
        assertTrue(messagingProperties.isEventEnabled());
    }

    @Test
    @DisplayName("Login in the kafka profile publishes user.logged_in with the username")
    void loginPublishesTheUsername() throws Exception {
        RegisterRequest user = TestDataBuilder.createValidRegisterRequest();
        given()
            .contentType(ContentType.JSON)
            .body(user)
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(201);
        given()
            .contentType(ContentType.JSON)
            .body(TestDataBuilder.createLoginRequest(user.email, user.password))
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(200);

        awaitMessages(2);
        JsonNode loggedIn = eventOfType("user.logged_in");
        assertEquals(user.username, loggedIn.get("username").asText(), loggedIn.toString());
        assertEquals(user.email, loggedIn.get("userEmail").asText(), loggedIn.toString());
        assertEquals(user.username, eventOfType("user.created").get("username").asText());
    }

    private JsonNode eventOfType(String eventType) throws Exception {
        List<String> payloads = new ArrayList<>();
        for (Message<String> message : sink.received()) {
            payloads.add(message.getPayload());
        }
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
}
