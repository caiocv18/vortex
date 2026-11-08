package br.com.vortex.application.config.security;

import br.com.vortex.application.service.AuthTokenVerifier;
import br.com.vortex.application.service.AuthTokens;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O cenário do achado, pela porta HTTP e com a cadeia real (JwtAuthenticationFilter, AuthServiceClient com
 * o proxy do Resilience4j e AuthTokenVerifier): o serviço de autorização responde 503 a tudo, então o
 * validate-token falha e o fallback valida o token localmente com a chave pública configurada em
 * {@code auth.jwt.public-key-location}. Como o serviço falso nunca valida nada, um 200 só sai do fallback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DirtiesContext
class AuthServiceFallbackIntegrationTest {

    private static final KeyPair AUTH_KEYS = AuthTokens.newRsaKeyPair();
    private static final AtomicInteger AUTH_SERVICE_CALLS = new AtomicInteger();
    private static final HttpServer UNAVAILABLE_AUTH_SERVICE;
    private static final Path AUTH_PUBLIC_KEY;

    static {
        try {
            UNAVAILABLE_AUTH_SERVICE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            UNAVAILABLE_AUTH_SERVICE.createContext("/", exchange -> {
                AUTH_SERVICE_CALLS.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            });
            UNAVAILABLE_AUTH_SERVICE.start();
            AUTH_PUBLIC_KEY = Files.createTempFile("vortex-auth-public-key", ".pem");
            Files.writeString(AUTH_PUBLIC_KEY, AuthTokens.toPem(AUTH_KEYS.getPublic()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void authServiceIsDown(DynamicPropertyRegistry registry) {
        registry.add("auth.service.url", () -> "http://127.0.0.1:" + UNAVAILABLE_AUTH_SERVICE.getAddress().getPort());
        registry.add("auth.jwt.public-key-location", AUTH_PUBLIC_KEY::toString);
    }

    @AfterAll
    static void stopFakeAuthService() throws IOException {
        UNAVAILABLE_AUTH_SERVICE.stop(0);
        Files.deleteIfExists(AUTH_PUBLIC_KEY);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void genuineAuthServiceTokenIsAcceptedWhileTheAuthServiceIsDown() {
        ResponseEntity<String> response = get(AuthTokens.authServiceToken(AUTH_KEYS));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(AUTH_SERVICE_CALLS.get() > 0, "o validate-token tem de ter sido tentado no serviço de autorização");
    }

    @Test
    void hs256TokenForgedWithTheApplicationSecretIsUnauthorizedWhileTheAuthServiceIsDown() {
        assertEquals(HttpStatus.UNAUTHORIZED, get(AuthTokens.forgedHs256Token()).getStatusCode());
    }

    @Test
    void tokenFromAnotherIssuerIsUnauthorizedWhileTheAuthServiceIsDown() {
        String token = AuthTokens.accessTokenClaims("https://emissor-falso.example")
                .expiration(AuthTokens.fromNow(Duration.ofMinutes(15)))
                .signWith(AUTH_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertEquals(HttpStatus.UNAUTHORIZED, get(token).getStatusCode());
    }

    @Test
    void expiredAuthServiceTokenIsUnauthorizedWhileTheAuthServiceIsDown() {
        String token = AuthTokens.accessTokenClaims(AuthTokenVerifier.DEFAULT_ISSUER)
                .expiration(AuthTokens.fromNow(Duration.ofMinutes(-5)))
                .signWith(AUTH_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertEquals(HttpStatus.UNAUTHORIZED, get(token).getStatusCode());
    }

    private ResponseEntity<String> get(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/tipos-produto", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
