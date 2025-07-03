package br.com.vortex.desafio.backend.controller;

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
class MovimentoControllerTest {

    @Autowired
    private MovimentoController movimentoController;

    @Test
    void testControllerLoads() {
        // This test will pass if the controller is autowired successfully
        assertNotNull(movimentoController);
    }

    @Test
    void testBuscarTodos() {
        // This test will verify that we can get all inventory movements
        ResponseEntity<?> response = movimentoController.buscarTodos();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
