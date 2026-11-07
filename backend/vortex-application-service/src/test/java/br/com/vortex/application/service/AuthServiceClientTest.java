package br.com.vortex.application.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Contrato HTTP do {@link AuthServiceClient} (RestClient) contra um serviço de autorização falso:
 * caminhos, corpo e cabeçalhos enviados e leitura da resposta. Sem proxy Spring, então sem os
 * fallbacks do Resilience4j: um erro HTTP aparece como falha do future.
 */
class AuthServiceClientTest {

    private static final String VALIDATE_RESPONSE =
            "{\"valid\":true,\"username\":\"ana\",\"email\":\"ana@vortex.dev\",\"roles\":[\"USER\",\"ADMIN\"],\"userId\":\"42\"}";

    private final AtomicReference<String> validateBody = new AtomicReference<>();
    private final AtomicReference<String> validateContentType = new AtomicReference<>();
    private final AtomicReference<String> userInfoAuthorization = new AtomicReference<>();
    private HttpServer server;
    private AuthServiceClient client;

    @BeforeEach
    void startFakeAuthService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/validate-token", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            validateBody.set(body);
            validateContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            if (body.contains("rejected")) {
                respond(exchange, 401, "{\"valid\":false,\"message\":\"Invalid token\"}");
            } else {
                respond(exchange, 200, VALIDATE_RESPONSE);
            }
        });
        server.createContext("/auth/userinfo", exchange -> {
            userInfoAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"username\":\"ana\",\"email\":\"ana@vortex.dev\"}");
        });
        server.start();
        client = new AuthServiceClient(RestClient.builder(), mock(JwtService.class),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopFakeAuthService() {
        server.stop(0);
    }

    @Test
    void validateTokenPostsTheTokenAsJsonAndReturnsTheAuthServiceFields() {
        Map<String, Object> response = client.validateToken("abc.def.ghi").join();

        assertEquals(Boolean.TRUE, response.get("valid"));
        assertEquals("ana", response.get("username"));
        assertEquals("ana@vortex.dev", response.get("email"));
        assertEquals(List.of("USER", "ADMIN"), response.get("roles"));
        assertEquals("42", response.get("userId"));
        assertTrue(validateContentType.get().startsWith("application/json"), validateContentType.get());
        assertEquals(Map.of("token", "abc.def.ghi"), JsonMapper.builder().build().readValue(validateBody.get(), Map.class));
    }

    @Test
    void validateTokenFailsWhenTheAuthServiceRejectsTheToken() {
        CompletionException failure = assertThrows(CompletionException.class,
                () -> client.validateToken("rejected").join());

        assertInstanceOf(RuntimeException.class, failure.getCause());
        assertEquals("Auth service validation failed", failure.getCause().getMessage());
    }

    @Test
    void getUserInfoSendsTheBearerToken() {
        Map<String, Object> response = client.getUserInfo("abc.def.ghi").join();

        assertEquals("ana", response.get("username"));
        assertEquals("Bearer abc.def.ghi", userInfoAuthorization.get());
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
