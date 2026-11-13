package br.com.vortex.application.controller;

import br.com.vortex.application.controller.RelatorioController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// O bean é o proxy da segurança de método: a leitura roda com o papel mais fraco que lê
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "VIEWER")
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
