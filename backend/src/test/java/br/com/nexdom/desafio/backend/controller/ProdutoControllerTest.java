package br.com.nexdom.desafio.backend.controller;

import br.com.nexdom.desafio.backend.dto.ProdutoDTO;
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
public class ProdutoControllerTest {

    @Autowired
    private ProdutoController produtoController;

    @Test
    public void testControllerLoads() {
        // This test will pass if the controller is autowired successfully
        assertNotNull(produtoController);
    }

    @Test
    public void testBuscarTodos() {
        // This test will verify that we can get all products
        ResponseEntity<?> response = produtoController.buscarTodos();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
