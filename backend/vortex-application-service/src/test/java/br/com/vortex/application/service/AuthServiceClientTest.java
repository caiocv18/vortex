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
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato HTTP do {@link AuthServiceClient} (RestClient) contra um serviço de autorização falso:
 * caminhos, corpo e cabeçalhos enviados e leitura da resposta. Sem proxy Spring, então sem os
 * fallbacks do Resilience4j: um erro HTTP aparece como falha do future, e os fallbacks são
 * chamados direto (a ligação pelo Resilience4j está em AuthServiceFallbackIntegrationTest).
 */
class AuthServiceClientTest {

    private static final KeyPair AUTH_KEYS = AuthTokens.newRsaKeyPair();
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
        client = clientWith(new AuthTokenVerifier(AUTH_KEYS.getPublic(), AuthTokenVerifier.DEFAULT_ISSUER));
    }

    private AuthServiceClient clientWith(AuthTokenVerifier verifier) {
        return new AuthServiceClient(RestClient.builder(), verifier,
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

    @Test
    void validateTokenFallbackAcceptsAnAuthServiceTokenCheckedWithThePublicKey() {
        Map<String, Object> response = client
                .validateTokenFallback(AuthTokens.authServiceToken(AUTH_KEYS), new RuntimeException("auth fora"))
                .join();

        // Mesmo formato do validate-token, que é o que o JwtAuthenticationFilter lê
        assertEquals(Boolean.TRUE, response.get("valid"));
        assertEquals(AuthTokens.USERNAME, response.get("username"));
        assertEquals(AuthTokens.EMAIL, response.get("email"));
        assertEquals(AuthTokens.ROLES, response.get("roles"));
        assertEquals(AuthTokens.USER_ID, response.get("userId"));
        assertEquals("local-validation", response.get("source"));
    }

    @Test
    void validateTokenFallbackRejectsAnHs256TokenForgedWithTheApplicationSecret() {
        Map<String, Object> response = client
                .validateTokenFallback(AuthTokens.forgedHs256Token(), new RuntimeException("auth fora"))
                .join();

        assertEquals(Boolean.FALSE, response.get("valid"));
        assertFalse(response.containsKey("username"));
    }

    @Test
    void validateTokenFallbackRejectsEveryTokenWithoutAPublicKey() {
        AuthServiceClient withoutKey = clientWith(new AuthTokenVerifier(null, AuthTokenVerifier.DEFAULT_ISSUER));

        Map<String, Object> genuine = withoutKey
                .validateTokenFallback(AuthTokens.authServiceToken(AUTH_KEYS), new RuntimeException("auth fora"))
                .join();
        Map<String, Object> forged = withoutKey
                .validateTokenFallback(AuthTokens.forgedHs256Token(), new RuntimeException("auth fora"))
                .join();

        assertEquals(Boolean.FALSE, genuine.get("valid"));
        assertEquals(Boolean.FALSE, forged.get("valid"));
    }

    @Test
    void getUserInfoFallbackReadsOnlyAVerifiedToken() {
        Map<String, Object> genuine = client
                .getUserInfoFallback(AuthTokens.authServiceToken(AUTH_KEYS), new RuntimeException("auth fora"))
                .join();
        Map<String, Object> forged = client
                .getUserInfoFallback(AuthTokens.forgedHs256Token(), new RuntimeException("auth fora"))
                .join();

        assertEquals(AuthTokens.USERNAME, genuine.get("username"));
        assertEquals(AuthTokens.ROLES, genuine.get("roles"));
        assertEquals(AuthTokens.USER_ID, genuine.get("userId"));
        assertEquals("Invalid token", forged.get("error"));
        assertFalse(forged.containsKey("username"));
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
