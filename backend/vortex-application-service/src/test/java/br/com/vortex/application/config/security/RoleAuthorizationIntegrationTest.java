package br.com.vortex.application.config.security;

import br.com.vortex.application.service.AuthServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A matriz de {@code docs/security/roles.md} pela porta HTTP, célula por célula, com a cadeia real:
 * JwtAuthenticationFilter, {@code @PreAuthorize} nos controllers e GlobalExceptionHandler. Só o validate-token
 * do serviço de autorização é simulado: o token é o nome de um papel, e a resposta traz esse papel em
 * {@code roles}. Cada célula "sim" é provada com 2xx e cada "não" com 403.
 *
 * <p>Os corpos de escrita são válidos de propósito. O {@code @PreAuthorize} roda depois da validação dos
 * argumentos, então um corpo inválido daria 400 e esconderia se a autorização recusou ou não.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class RoleAuthorizationIntegrationTest {

    private static final List<String> ROLES = List.of("VIEWER", "USER", "MANAGER", "ADMIN");
    /** Token cuja resposta do validate-token não traz a chave {@code roles}. */
    private static final String NO_ROLES_TOKEN = "NO_ROLES";
    /** Token cuja resposta do validate-token traz {@code roles} vazio. */
    private static final String EMPTY_ROLES_TOKEN = "EMPTY_ROLES";
    /** Produto da massa de teste sem movimento de estoque: um DELETE que passasse o apagaria de fato. */
    private static final String PRODUCT_WITHOUT_MOVEMENTS = "/api/produtos/20";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @BeforeEach
    void eachTokenValidatesAsTheRoleItNames() {
        when(authServiceClient.validateToken(anyString())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(validationFor(invocation.getArgument(0))));
    }

    static Stream<ApiCall> reads() {
        return Stream.of(
                ApiCall.get("/api/tipos-produto"),
                ApiCall.get("/api/tipos-produto/1"),
                ApiCall.get("/api/produtos"),
                ApiCall.get("/api/produtos/1"),
                ApiCall.get("/api/movimentos"),
                ApiCall.get("/api/movimentos/1"),
                ApiCall.get("/api/relatorios/lucro-por-produto"),
                ApiCall.get("/api/relatorios/produtos-por-tipo?tipoProdutoId=1"));
    }

    static Stream<Arguments> everyRoleTimesEveryRead() {
        return ROLES.stream().flatMap(role -> reads().map(call -> Arguments.of(role, call)));
    }

    static Stream<ApiCall> writes() {
        Map<String, Object> tipo = Map.of("nome", "Tipo que não deveria existir");
        Map<String, Object> produto = produto("Produto que não deveria existir");
        Map<String, Object> movimento = movimento(1, 1);
        return Stream.of(
                new ApiCall(HttpMethod.POST, "/api/tipos-produto", tipo),
                new ApiCall(HttpMethod.PUT, "/api/tipos-produto/1", tipo),
                new ApiCall(HttpMethod.DELETE, "/api/tipos-produto/1", null),
                new ApiCall(HttpMethod.POST, "/api/produtos", produto),
                new ApiCall(HttpMethod.PUT, "/api/produtos/1", produto),
                new ApiCall(HttpMethod.DELETE, "/api/produtos/1", null),
                new ApiCall(HttpMethod.POST, "/api/movimentos", movimento),
                new ApiCall(HttpMethod.PUT, "/api/movimentos/1", movimento),
                new ApiCall(HttpMethod.DELETE, "/api/movimentos/1", null));
    }

    static Stream<ApiCall> queues() {
        return Stream.of(
                ApiCall.get("/api/queues"),
                ApiCall.get("/api/queues/status"),
                ApiCall.get("/api/queues/estoque"),
                ApiCall.get("/api/queues/estoque/messages"),
                new ApiCall(HttpMethod.POST, "/api/queues/estoque/test-message", Map.of("texto", "teste")));
    }

    static Stream<ApiCall> everyCall() {
        return Stream.of(reads(), writes(), queues()).flatMap(calls -> calls);
    }

    @Test
    void requestWithoutTokenIsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/produtos", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(authServiceClient, never()).validateToken(anyString());
    }

    @ParameterizedTest(name = "{0} lê {1}")
    @MethodSource("everyRoleTimesEveryRead")
    void everyRoleReads(String role, ApiCall call) {
        assertEquals(HttpStatus.OK, send(call, role).getStatusCode());
    }

    @ParameterizedTest(name = "VIEWER não escreve: {0}")
    @MethodSource("writes")
    void viewerCannotWrite(ApiCall call) {
        assertEquals(HttpStatus.FORBIDDEN, send(call, "VIEWER").getStatusCode());
    }

    @Test
    void deniedDeleteLeavesTheProductInPlace() {
        assertEquals(HttpStatus.FORBIDDEN,
                send(new ApiCall(HttpMethod.DELETE, PRODUCT_WITHOUT_MOVEMENTS, null), "VIEWER").getStatusCode());

        assertEquals(HttpStatus.OK, send(ApiCall.get(PRODUCT_WITHOUT_MOVEMENTS), "VIEWER").getStatusCode());
    }

    @ParameterizedTest(name = "{0} cria, altera e apaga tipo de produto")
    @ValueSource(strings = {"USER", "MANAGER", "ADMIN"})
    void writerRolesManageTiposProduto(String role) {
        String path = "/api/tipos-produto/" + create(role, "/api/tipos-produto", Map.of("nome", "Tipo criado por " + role));

        assertEquals(HttpStatus.OK, put(role, path, Map.of("nome", "Tipo alterado por " + role)));
        assertEquals(HttpStatus.NO_CONTENT, delete(role, path));
    }

    @ParameterizedTest(name = "{0} cria, altera e apaga produto")
    @ValueSource(strings = {"USER", "MANAGER", "ADMIN"})
    void writerRolesManageProdutos(String role) {
        String path = "/api/produtos/" + create(role, "/api/produtos", produto("Produto criado por " + role));

        assertEquals(HttpStatus.OK, put(role, path, produto("Produto alterado por " + role)));
        assertEquals(HttpStatus.NO_CONTENT, delete(role, path));
    }

    @ParameterizedTest(name = "{0} cria, altera e apaga movimento")
    @ValueSource(strings = {"USER", "MANAGER", "ADMIN"})
    void writerRolesManageMovimentos(String role) {
        // Produto próprio, para que o efeito no estoque não toque a massa compartilhada
        String productId = create(role, "/api/produtos", produto("Produto dos movimentos de " + role));
        String path = "/api/movimentos/" + create(role, "/api/movimentos", movimento(Long.parseLong(productId), 1));

        assertEquals(HttpStatus.OK, put(role, path, movimento(Long.parseLong(productId), 2)));
        assertEquals(HttpStatus.NO_CONTENT, delete(role, path));
        assertEquals(HttpStatus.NO_CONTENT, delete(role, "/api/produtos/" + productId));
    }

    @ParameterizedTest(name = "token sem papel não acessa {0}")
    @MethodSource("everyCall")
    void tokenWithoutRolesIsForbidden(ApiCall call) {
        assertEquals(HttpStatus.FORBIDDEN, send(call, NO_ROLES_TOKEN).getStatusCode(), "sem a chave roles");
        assertEquals(HttpStatus.FORBIDDEN, send(call, EMPTY_ROLES_TOKEN).getStatusCode(), "roles vazio");
    }

    @ParameterizedTest(name = "{0} não alcança as filas")
    @ValueSource(strings = {"VIEWER", "USER", "MANAGER"})
    void onlyAdminReachesQueues(String role) {
        queues().forEach(call ->
                assertEquals(HttpStatus.FORBIDDEN, send(call, role).getStatusCode(), call + " como " + role));
    }

    @ParameterizedTest(name = "ADMIN alcança {0}")
    @MethodSource("queues")
    void adminReachesEveryQueueCall(ApiCall call) {
        assertEquals(HttpStatus.OK, send(call, "ADMIN").getStatusCode());
    }

    /** POST que precisa dar 201; devolve o id criado. */
    private String create(String role, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(body, bearer(role)), JSON_OBJECT);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), "POST " + path + " como " + role);
        assertNotNull(created.getBody());
        assertNotNull(created.getBody().get("id"));
        return String.valueOf(created.getBody().get("id"));
    }

    private HttpStatus put(String role, String path, Map<String, Object> body) {
        return HttpStatus.valueOf(send(new ApiCall(HttpMethod.PUT, path, body), role).getStatusCode().value());
    }

    private HttpStatus delete(String role, String path) {
        return HttpStatus.valueOf(send(new ApiCall(HttpMethod.DELETE, path, null), role).getStatusCode().value());
    }

    private ResponseEntity<String> send(ApiCall call, String token) {
        return restTemplate.exchange(call.path(), call.method(), new HttpEntity<>(call.body(), bearer(token)), String.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static Map<String, Object> produto(String descricao) {
        return Map.of("descricao", descricao, "valorFornecedor", 10, "quantidadeEmEstoque", 5, "tipoProdutoId", 1);
    }

    private static Map<String, Object> movimento(long produtoId, int quantidade) {
        return Map.of("tipoMovimentacao", "ENTRADA", "quantidadeMovimentada", quantidade, "produtoId", produtoId);
    }

    private static Map<String, Object> validationFor(String token) {
        String user = token.toLowerCase(Locale.ROOT);
        Map<String, Object> validation = new HashMap<>();
        validation.put("valid", true);
        validation.put("username", user);
        validation.put("email", user + "@vortex.dev");
        validation.put("userId", "1");
        switch (token) {
            case NO_ROLES_TOKEN -> {
                // a resposta não traz roles
            }
            case EMPTY_ROLES_TOKEN -> validation.put("roles", List.of());
            default -> validation.put("roles", List.of(token));
        }
        return validation;
    }

    /** Uma chamada à API; o {@code toString} vira o nome do caso parametrizado. */
    record ApiCall(HttpMethod method, String path, Object body) {

        static ApiCall get(String path) {
            return new ApiCall(HttpMethod.GET, path, null);
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}
