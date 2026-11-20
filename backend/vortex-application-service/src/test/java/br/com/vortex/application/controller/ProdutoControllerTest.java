package br.com.vortex.application.controller;

import br.com.vortex.application.dto.ProdutoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// O bean é o proxy da segurança de método: a leitura roda com o papel mais fraco que lê
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "VIEWER")
class ProdutoControllerTest {

    @Autowired
    private ProdutoController produtoController;

    @Test
    void testControllerLoads() {
        assertNotNull(produtoController);
    }

    @Test
    void testBuscarPaginado() {
        ResponseEntity<PagedModel<ProdutoDTO>> response =
                produtoController.buscar(null, PageRequest.of(0, 5, Sort.by("descricao")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PagedModel<ProdutoDTO> pagina = response.getBody();
        assertNotNull(pagina);
        assertNotNull(pagina.getMetadata());
        assertEquals(5, pagina.getMetadata().size());
        assertEquals(0, pagina.getMetadata().number());
        assertTrue(pagina.getMetadata().totalElements() > 5, "o seed tem 20 produtos");
        assertTrue(pagina.getMetadata().totalPages() > 1);
        assertEquals(5, pagina.getContent().size());
    }
}
