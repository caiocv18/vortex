package br.com.vortex.desafio.backend.controller;

import br.com.vortex.desafio.backend.dto.TipoProdutoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TipoProdutoControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testCreateTipoProduto() {
        // Arrange
        TipoProdutoDTO tipoProdutoDTO = new TipoProdutoDTO();
        tipoProdutoDTO.setNome("Teste Integração");

        // Act
        ResponseEntity<TipoProdutoDTO> response = restTemplate.postForEntity(
                "/api/tipos-produto",
                tipoProdutoDTO,
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
        ResponseEntity<TipoProdutoDTO> createResponse = restTemplate.postForEntity(
                "/api/tipos-produto",
                tipoProdutoDTO,
                TipoProdutoDTO.class);

        assertNotNull(createResponse.getBody());
        Long id = createResponse.getBody().getId();

        // Act
        ResponseEntity<TipoProdutoDTO> response = restTemplate.getForEntity(
                "/api/tipos-produto/" + id,
                TipoProdutoDTO.class);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(id, response.getBody().getId());
        assertEquals("Teste Get By ID", response.getBody().getNome());
    }
}