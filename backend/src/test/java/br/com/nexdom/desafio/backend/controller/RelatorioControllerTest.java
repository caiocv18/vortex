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
class RelatorioControllerTest {

    @Autowired
    private RelatorioController relatorioController;

    @Test
    void testControllerLoads() {
        // This test will pass if the controller is autowired successfully
        assertNotNull(relatorioController);
    }

    @Test
    void testGerarRelatorioLucroPorProduto() {
        // This test will verify that we can generate a profit report by product
        ResponseEntity<?> response = relatorioController.gerarRelatorioLucroPorProduto();

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
