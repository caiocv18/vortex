package br.com.vortex.application.controller;

import br.com.vortex.application.dto.MovimentoEstoqueDTO;
import br.com.vortex.application.model.enums.TipoMovimentacao;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// O bean é o proxy da segurança de método: a leitura roda com o papel mais fraco que lê
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "VIEWER")
class MovimentoControllerTest {

    @Autowired
    private MovimentoController movimentoController;

    @Test
    void testControllerLoads() {
        assertNotNull(movimentoController);
    }

    @Test
    void testBuscarPaginado() {
        ResponseEntity<PagedModel<MovimentoEstoqueDTO>> response = movimentoController.buscar(null, null,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "dataMovimento")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PagedModel<MovimentoEstoqueDTO> pagina = response.getBody();
        assertNotNull(pagina);
        assertNotNull(pagina.getMetadata());
        assertEquals(5, pagina.getMetadata().size());
        assertTrue(pagina.getMetadata().totalElements() > 5, "o seed tem 20 movimentos");
        assertTrue(pagina.getMetadata().totalPages() > 1);
        assertEquals(5, pagina.getContent().size());
    }

    @Test
    void testBuscarSoAsSaidas() {
        PagedModel<MovimentoEstoqueDTO> pagina = movimentoController.buscar(null, TipoMovimentacao.SAIDA,
                PageRequest.of(0, 100)).getBody();

        assertNotNull(pagina);
        assertFalse(pagina.getContent().isEmpty(), "o seed tem 10 saídas");
        assertTrue(pagina.getContent().stream().allMatch(m -> m.getTipoMovimentacao() == TipoMovimentacao.SAIDA));
        assertTrue(pagina.getContent().stream().allMatch(m -> m.getProdutoDescricao() != null));
    }
}
