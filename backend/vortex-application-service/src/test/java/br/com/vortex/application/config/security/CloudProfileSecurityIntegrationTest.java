package br.com.vortex.application.config.security;

import br.com.vortex.application.config.PostgresContainerSupport;
import br.com.vortex.application.service.AuthServiceClient;
import br.com.vortex.application.service.TipoProdutoService;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * A demo pública como a EC2 a sobe ({@code cloud,postgres}, num PostgreSQL 18 real), pela porta HTTP. O fechamento
 * vem em camadas e cada uma é provada:
 * <ol>
 *   <li>as propriedades e o {@code @Profile} nem registram Swagger, {@code /v3/api-docs}, console do H2 nem
 *       {@code /api/test};</li>
 *   <li>a cadeia de segurança nega esses caminhos mesmo assim: 401 sem token, e 401 ou 403 com token;</li>
 *   <li>o validador roda em modo produção, e o contexto só sobe porque o segredo JWT é forte e o banco não é H2.</li>
 * </ol>
 * O segredo JWT é gerado na hora, como a EC2 faria com {@code openssl rand}. Só o validate-token do serviço de
 * autorização é simulado: o token é o nome do papel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"cloud", "postgres"})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CloudProfileSecurityIntegrationTest extends PostgresContainerSupport {

    private static final String DEMO_ORIGIN = "https://vortex.example.com";
    private static final long FAILING_ID = 424_242L;
    private static final String INTERNAL_DETAIL = "detalhe interno que não pode vazar";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ServletContext servletContext;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @MockitoSpyBean
    private TipoProdutoService tipoProdutoService;

    @DynamicPropertySource
    static void cloudEnvironment(DynamicPropertyRegistry registry) {
        byte[] secret = new byte[48];
        new SecureRandom().nextBytes(secret);
        String jwtSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        registry.add("JWT_SECRET", () -> jwtSecret);
        registry.add("VORTEX_CLOUD_CORS_ORIGINS", () -> DEMO_ORIGIN);
    }

    @BeforeEach
    void eachTokenValidatesAsTheRoleItNames() {
        when(authServiceClient.validateToken(anyString())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                Map.of("valid", true, "username", "cloud-test", "userId", "1",
                        "roles", List.of(invocation.getArgument(0, String.class)))));
    }

    static List<String> closedPaths() {
        return List.of("/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs", "/h2-console", "/h2-console/",
                "/api/test/public");
    }

    @Test
    void docsConsoleAndTestEndpointsAreNotEvenRegistered() {
        List<String> servletMappings = servletContext.getServletRegistrations().values().stream()
                .flatMap(registration -> registration.getMappings().stream())
                .toList();
        List<String> handlerPatterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .toList();

        assertFalse(servletMappings.stream().anyMatch(mapping -> mapping.startsWith("/h2-console")), servletMappings.toString());
        assertFalse(handlerPatterns.stream().anyMatch(pattern ->
                pattern.startsWith("/api/test") || pattern.startsWith("/v3/api-docs") || pattern.startsWith("/swagger")),
                handlerPatterns.toString());
    }

    @ParameterizedTest
    @MethodSource("closedPaths")
    void anonymousCallsAreRefused(String path) {
        assertEquals(HttpStatus.UNAUTHORIZED, get(path, null).getStatusCode(), path);
    }

    /**
     * O {@code JwtAuthenticationFilter} nem lê o token em {@code /swagger-ui*} e {@code /v3/api-docs}
     * ({@code shouldNotFilter}), então ali a requisição segue anônima e a recusa sai 401. Nos demais o token é lido e
     * o {@code denyAll} responde 403. Nenhum caminho dá 2xx.
     */
    static Stream<Arguments> closedPathsWithAToken() {
        return Stream.of(
                Arguments.of("/swagger-ui/index.html", HttpStatus.UNAUTHORIZED),
                Arguments.of("/swagger-ui.html", HttpStatus.UNAUTHORIZED),
                Arguments.of("/v3/api-docs", HttpStatus.UNAUTHORIZED),
                Arguments.of("/h2-console", HttpStatus.FORBIDDEN),
                Arguments.of("/h2-console/", HttpStatus.FORBIDDEN),
                Arguments.of("/api/test/public", HttpStatus.FORBIDDEN));
    }

    @ParameterizedTest
    @MethodSource("closedPathsWithAToken")
    void aValidTokenDoesNotOpenThemEither(String path, HttpStatus expected) {
        assertEquals(expected, get(path, "VIEWER").getStatusCode(), path);
    }

    @Test
    void healthStaysPublic() {
        assertEquals(HttpStatus.OK, get("/health", null).getStatusCode());
    }

    /** Sem o permitAll do despacho de erro, o 403 do {@code @PreAuthorize} chegaria ao cliente como 401. */
    @Test
    void viewerReadsTheApiAndAWriteIsForbiddenNotUnauthorized() {
        assertEquals(HttpStatus.OK, get("/api/tipos-produto", "VIEWER").getStatusCode());

        ResponseEntity<String> write = restTemplate.exchange("/api/tipos-produto", HttpMethod.POST,
                new HttpEntity<>(Map.of("nome", "Tipo que um VIEWER não cria"), bearer("VIEWER")), String.class);

        assertEquals(HttpStatus.FORBIDDEN, write.getStatusCode());
    }

    @Test
    void anUnexpectedErrorComesBackGenericWithItsId() {
        doThrow(new IllegalStateException(INTERNAL_DETAIL)).when(tipoProdutoService).buscarPorId(FAILING_ID);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/api/tipos-produto/" + FAILING_ID,
                HttpMethod.GET, new HttpEntity<>(bearer("VIEWER")), JSON_OBJECT);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("errorId"));
        assertEquals("Ocorreu um erro interno no servidor. ID do erro: " + body.get("errorId"), body.get("message"));
        assertNull(body.get("technicalDetails"));
        assertFalse(body.toString().contains(INTERNAL_DETAIL), body.toString());
    }

    @Test
    void corsAnswersOnlyTheConfiguredOrigin() {
        ResponseEntity<String> configured = preflight(DEMO_ORIGIN);
        ResponseEntity<String> localFrontend = preflight("http://localhost:5173");

        assertEquals(HttpStatus.OK, configured.getStatusCode());
        assertEquals(DEMO_ORIGIN, configured.getHeaders().getAccessControlAllowOrigin());
        assertEquals(HttpStatus.FORBIDDEN, localFrontend.getStatusCode());
        assertNull(localFrontend.getHeaders().getAccessControlAllowOrigin());
    }

    @Test
    void corsNeverAllowsCredentials() {
        // A API do app é stateless, só lê Bearer e não usa cookie: sem credencial que o navegador anexe
        // sozinho, o CSRF desligado no SecurityConfig não tem superfície. Sem Access-Control-Allow-Credentials,
        // nenhuma origem lê a resposta de uma requisição com credenciais; isso é defesa extra, não a proteção
        // contra CSRF.
        ResponseEntity<String> configured = preflight(DEMO_ORIGIN);

        assertEquals(HttpStatus.OK, configured.getStatusCode());
        assertFalse(configured.getHeaders().getAccessControlAllowCredentials());
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = token == null ? new HttpHeaders() : bearer(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> preflight(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        return restTemplate.exchange("/api/tipos-produto", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
