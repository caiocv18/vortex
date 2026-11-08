package br.com.vortex.application.controller;

import br.com.vortex.application.dto.TipoProdutoDTO;
import br.com.vortex.application.service.AuthServiceClient;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa a API de tipos de produto pela porta HTTP, com a cadeia de segurança real: as rotas
 * /api/** exigem um Bearer token, que o JwtAuthenticationFilter valida no serviço de autorização.
 * Só a chamada remota ({@link AuthServiceClient}) é simulada, com a resposta do validate-token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class TipoProdutoControllerIntegrationTest {

    private static final String TEST_TOKEN = "integration-test-token";

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @BeforeEach
    void authServiceAcceptsTheTestToken() {
        Map<String, Object> validation = Map.of(
                "valid", true,
                "username", "integration-test",
                "email", "integration-test@vortex.dev",
                "roles", List.of("USER"),
                "userId", "1");
        when(authServiceClient.validateToken(TEST_TOKEN)).thenReturn(CompletableFuture.completedFuture(validation));
    }

    @Test
    void testCreateTipoProduto() {
        // Arrange
        TipoProdutoDTO tipoProdutoDTO = new TipoProdutoDTO();
        tipoProdutoDTO.setNome("Teste Integração");

        // Act
        ResponseEntity<TipoProdutoDTO> response = restTemplate.exchange(
                "/api/tipos-produto",
                HttpMethod.POST,
                authenticated(tipoProdutoDTO),
                TipoProdutoDTO.class);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertEquals("Teste Integração", response.getBody().getNome());
    }

    @Test
    void testGetTipoProdutoById() {
        // Arrange - First create a tipo produto
        TipoProdutoDTO tipoProdutoDTO = new TipoProdutoDTO();
        tipoProdutoDTO.setNome("Teste Get By ID");
        ResponseEntity<TipoProdutoDTO> createResponse = restTemplate.exchange(
                "/api/tipos-produto",
                HttpMethod.POST,
                authenticated(tipoProdutoDTO),
                TipoProdutoDTO.class);

        assertNotNull(createResponse.getBody());
        Long id = createResponse.getBody().getId();

        // Act
        ResponseEntity<TipoProdutoDTO> response = restTemplate.exchange(
                "/api/tipos-produto/" + id,
                HttpMethod.GET,
                authenticated(null),
                TipoProdutoDTO.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(id, response.getBody().getId());
        assertEquals("Teste Get By ID", response.getBody().getNome());
    }

    @Test
    void testCreateTipoProdutoWithoutTokenIsUnauthorized() {
        TipoProdutoDTO tipoProdutoDTO = new TipoProdutoDTO();
        tipoProdutoDTO.setNome("Teste Sem Token");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/tipos-produto",
                tipoProdutoDTO,
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(authServiceClient, never()).validateToken(anyString());
    }

    @Test
    void testTokenRejectedByAuthServiceIsUnauthorized() {
        when(authServiceClient.validateToken("rejected-token"))
                .thenReturn(CompletableFuture.completedFuture(Map.of("valid", false)));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("rejected-token");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/tipos-produto",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private static HttpEntity<TipoProdutoDTO> authenticated(TipoProdutoDTO body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TEST_TOKEN);
        return new HttpEntity<>(body, headers);
    }
}
