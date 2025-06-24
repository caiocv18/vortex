package br.com.nexdom.desafio.backend.controller;

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
class ProdutoControllerTest {

    @Autowired
    private ProdutoController produtoController;

    @Test
    void testControllerLoads() {
        assertNotNull(produtoController);
    }

    @Test
    void testBuscarTodos() {
        ResponseEntity<?> response = produtoController.buscarTodos();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
