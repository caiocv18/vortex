package br.com.vortex.application.controller;

import br.com.vortex.application.service.AuthServiceClient;
import br.com.vortex.application.service.MessageBrokerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O usuário do movimento, da porta HTTP ao {@code userId} do evento, com a cadeia real: JwtAuthenticationFilter,
 * {@code @PreAuthorize}, MovimentoController e MovimentoEstoqueService. Só o validate-token do serviço de
 * autorização e o broker são simulados; o broker responde disponível, para o serviço publicar.
 *
 * <p>O pipeline de dados junta movimentos e logins pelo HMAC do username, então o {@code userId} tem de ser o
 * username da validação, nunca o e-mail (os dois vêm diferentes de propósito).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class MovimentoUserIdIntegrationTest {

    private static final String TOKEN = "token-da-alice";
    private static final String USERNAME = "alice.estoque";
    private static final String EMAIL = "alice@vortex.dev";
    /** Token cuja validação não traz username: o filtro cai no e-mail. */
    private static final String TOKEN_SEM_USERNAME = "token-sem-username";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @MockitoBean
    private MessageBrokerService messageBrokerService;

    @BeforeEach
    void setUp() {
        when(authServiceClient.validateToken(anyString())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(validationFor(invocation.getArgument(0))));
        when(messageBrokerService.isAvailable()).thenReturn(true);
    }

    @Test
    void movementEventCarriesTheUsernameOfTheToken() {
        String productId = create(TOKEN, "/api/produtos", produto("Produto do userId"));
        String movementId = create(TOKEN, "/api/movimentos", movimento(Long.parseLong(productId)));

        verify(messageBrokerService).publicarMovimentoEstoque(any(), any(), any(), eq(USERNAME));
        // A auditoria do mesmo fluxo herda o usuário
        verify(messageBrokerService).publicarAuditoria(
                eq("MOVIMENTO_CRIADO"), eq("MovimentoEstoque"), any(), any(), eq(USERNAME), eq("SUCCESS"), any());

        cleanUp(TOKEN, movementId, productId);
    }

    @Test
    void withoutUsernameTheEventFallsBackToTheEmail() {
        // Caminho de reserva do filtro: o evento sai com o e-mail, que não junta com login nenhum no pipeline
        String productId = create(TOKEN_SEM_USERNAME, "/api/produtos", produto("Produto do fallback"));
        String movementId = create(TOKEN_SEM_USERNAME, "/api/movimentos", movimento(Long.parseLong(productId)));

        verify(messageBrokerService).publicarMovimentoEstoque(any(), any(), any(), eq(EMAIL));

        cleanUp(TOKEN_SEM_USERNAME, movementId, productId);
    }

    /** POST que precisa dar 201; devolve o id criado. */
    private String create(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(body, bearer(token)), JSON_OBJECT);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), "POST " + path);
        assertNotNull(created.getBody());
        assertNotNull(created.getBody().get("id"));
        return String.valueOf(created.getBody().get("id"));
    }

    /** Apaga o movimento e o produto próprios, para não deixar efeito na massa compartilhada. */
    private void cleanUp(String token, String movementId, String productId) {
        for (String path : List.of("/api/movimentos/" + movementId, "/api/produtos/" + productId)) {
            ResponseEntity<String> deleted = restTemplate.exchange(
                    path, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), String.class);
            assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode(), "DELETE " + path);
        }
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static Map<String, Object> produto(String descricao) {
        return Map.of("descricao", descricao, "valorFornecedor", 10, "quantidadeEmEstoque", 5, "tipoProdutoId", 1);
    }

    private static Map<String, Object> movimento(long produtoId) {
        return Map.of("tipoMovimentacao", "ENTRADA", "quantidadeMovimentada", 1, "produtoId", produtoId);
    }

    private static Map<String, Object> validationFor(String token) {
        Map<String, Object> validation = new HashMap<>();
        validation.put("valid", true);
        validation.put("email", EMAIL);
        validation.put("userId", "1");
        validation.put("roles", List.of("USER"));
        if (TOKEN.equals(token)) {
            validation.put("username", USERNAME);
        }
        return validation;
    }
}
