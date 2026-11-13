package br.com.vortex.application.config.security;

import br.com.vortex.application.dto.TipoProdutoDTO;
import br.com.vortex.application.service.AuthServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
 * A matriz de {@code docs/security/roles.md} pela porta HTTP, com a cadeia real: JwtAuthenticationFilter,
 * {@code @PreAuthorize} nos controllers e GlobalExceptionHandler. Só o validate-token do serviço de
 * autorização é simulado: o token é o nome de um papel, e a resposta traz esse papel em {@code roles}.
 *
 * <p>Os corpos de escrita são válidos de propósito. O {@code @PreAuthorize} roda depois da validação dos
 * argumentos, então um corpo inválido daria 400 e esconderia se a autorização recusou ou não.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class RoleAuthorizationIntegrationTest {

    /** Token cuja resposta do validate-token não traz a chave {@code roles}. */
    private static final String NO_ROLES_TOKEN = "NO_ROLES";
    /** Token cuja resposta do validate-token traz {@code roles} vazio. */
    private static final String EMPTY_ROLES_TOKEN = "EMPTY_ROLES";

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

    static Stream<ApiCall> writes() {
        Map<String, Object> tipo = Map.of("nome", "Tipo que não deveria existir");
        Map<String, Object> produto = Map.of(
                "descricao", "Produto que não deveria existir",
                "valorFornecedor", 10,
                "quantidadeEmEstoque", 1,
                "tipoProdutoId", 1);
        Map<String, Object> movimento = Map.of(
                "tipoMovimentacao", "ENTRADA",
                "quantidadeMovimentada", 1,
                "produtoId", 1);
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

    @ParameterizedTest(name = "VIEWER lê {0}")
    @MethodSource("reads")
    void viewerReads(ApiCall call) {
        assertEquals(HttpStatus.OK, send(call, "VIEWER").getStatusCode());
    }

    @ParameterizedTest(name = "VIEWER não escreve: {0}")
    @MethodSource("writes")
    void viewerCannotWrite(ApiCall call) {
        assertEquals(HttpStatus.FORBIDDEN, send(call, "VIEWER").getStatusCode());
    }

    @Test
    void deniedDeleteLeavesTheProductInPlace() {
        assertEquals(HttpStatus.FORBIDDEN, send(new ApiCall(HttpMethod.DELETE, "/api/produtos/2", null), "VIEWER").getStatusCode());

        assertEquals(HttpStatus.OK, send(ApiCall.get("/api/produtos/2"), "VIEWER").getStatusCode());
    }

    @ParameterizedTest(name = "{0} cria, altera e apaga")
    @ValueSource(strings = {"USER", "MANAGER", "ADMIN"})
    void writerRolesCreateUpdateAndDelete(String role) {
        ResponseEntity<TipoProdutoDTO> created = restTemplate.exchange(
                "/api/tipos-produto",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("nome", "Tipo criado por " + role), bearer(role)),
                TipoProdutoDTO.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        String path = "/api/tipos-produto/" + created.getBody().getId();

        assertEquals(HttpStatus.OK,
                send(new ApiCall(HttpMethod.PUT, path, Map.of("nome", "Tipo alterado por " + role)), role).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, send(new ApiCall(HttpMethod.DELETE, path, null), role).getStatusCode());
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

    @Test
    void adminReachesQueues() {
        assertEquals(HttpStatus.OK, send(ApiCall.get("/api/queues"), "ADMIN").getStatusCode());
        assertEquals(HttpStatus.OK, send(ApiCall.get("/api/queues/status"), "ADMIN").getStatusCode());
    }

    private ResponseEntity<String> send(ApiCall call, String token) {
        return restTemplate.exchange(call.path(), call.method(), new HttpEntity<>(call.body(), bearer(token)), String.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
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
