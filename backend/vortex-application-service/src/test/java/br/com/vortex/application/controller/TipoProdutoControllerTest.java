package br.com.vortex.application.controller;

import br.com.vortex.application.dto.TipoProdutoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// O bean é o proxy da segurança de método: criar exige um papel de escrita
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "USER")
class TipoProdutoControllerTest {

    @Autowired
    private TipoProdutoController tipoProdutoController;

    @Test
    void testControllerLoads() {
        assertNotNull(tipoProdutoController);
    }

    @Test
    void testCriar() {
        TipoProdutoDTO tipoProdutoDTO = new TipoProdutoDTO();
        tipoProdutoDTO.setNome("Teste Unitário");

        ResponseEntity<TipoProdutoDTO> response = tipoProdutoController.criar(tipoProdutoDTO);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertEquals("Teste Unitário", response.getBody().getNome());
    }
}
