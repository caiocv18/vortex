package br.com.nexdom.desafio.backend.controller;

import br.com.nexdom.desafio.backend.dto.TipoProdutoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class TipoProdutoControllerTest {

    @Autowired
    private TipoProdutoController tipoProdutoController;

    @Test
    public void testControllerLoads() {
        // This test will pass if the controller is autowired successfully
        assertNotNull(tipoProdutoController);
    }

    @Test
    public void testCriar() {
        // Arrange
        TipoProdutoDTO tipoProdutoDTO = new TipoProdutoDTO();
        tipoProdutoDTO.setNome("Teste Unitário");

        // Act
        ResponseEntity<TipoProdutoDTO> response = tipoProdutoController.criar(tipoProdutoDTO);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertEquals("Teste Unitário", response.getBody().getNome());
    }
}
