package br.com.nexdom.desafio.backend.controller;

import br.com.nexdom.desafio.backend.dto.TipoProdutoDTO;
import br.com.nexdom.desafio.backend.service.TipoProdutoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TipoProdutoControllerUnitTest {

    @Mock
    private TipoProdutoService tipoProdutoService;

    @InjectMocks
    private TipoProdutoController tipoProdutoController;

    private TipoProdutoDTO tipoProdutoDTO;
    private List<TipoProdutoDTO> tipoProdutoDTOList;

    @BeforeEach
    void setUp() {
        tipoProdutoDTO = new TipoProdutoDTO();
        tipoProdutoDTO.setId(1L);
        tipoProdutoDTO.setNome("Eletrônicos");

        TipoProdutoDTO tipoProdutoDTO2 = new TipoProdutoDTO();
        tipoProdutoDTO2.setId(2L);
        tipoProdutoDTO2.setNome("Roupas");

        TipoProdutoDTO tipoProdutoDTO3 = new TipoProdutoDTO();
        tipoProdutoDTO3.setId(3L);
        tipoProdutoDTO3.setNome("Alimentos");

        tipoProdutoDTOList = Arrays.asList(tipoProdutoDTO, tipoProdutoDTO2, tipoProdutoDTO3);
    }

    @Test
    void testCriar() {
        TipoProdutoDTO inputDTO = new TipoProdutoDTO();
        inputDTO.setNome("Eletrônicos");
        
        when(tipoProdutoService.criar(any(TipoProdutoDTO.class))).thenReturn(tipoProdutoDTO);

        ResponseEntity<TipoProdutoDTO> response = tipoProdutoController.criar(inputDTO);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("Eletrônicos", response.getBody().getNome());
    }

    @Test
    void testBuscarTodos() {
        when(tipoProdutoService.buscarTodos()).thenReturn(tipoProdutoDTOList);

        ResponseEntity<List<TipoProdutoDTO>> response = tipoProdutoController.buscarTodos();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        assertEquals("Eletrônicos", response.getBody().get(0).getNome());
        assertEquals("Roupas", response.getBody().get(1).getNome());
        assertEquals("Alimentos", response.getBody().get(2).getNome());
    }

    @Test
    void testBuscarPorId() {
        when(tipoProdutoService.buscarPorId(1L)).thenReturn(tipoProdutoDTO);

        ResponseEntity<TipoProdutoDTO> response = tipoProdutoController.buscarPorId(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("Eletrônicos", response.getBody().getNome());
    }

    @Test
    void testAtualizar() {
        TipoProdutoDTO inputDTO = new TipoProdutoDTO();
        inputDTO.setNome("Eletrônicos Atualizados");
        
        TipoProdutoDTO updatedDTO = new TipoProdutoDTO();
        updatedDTO.setId(1L);
        updatedDTO.setNome("Eletrônicos Atualizados");
        
        when(tipoProdutoService.atualizar(eq(1L), any(TipoProdutoDTO.class))).thenReturn(updatedDTO);

        ResponseEntity<TipoProdutoDTO> response = tipoProdutoController.atualizar(1L, inputDTO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("Eletrônicos Atualizados", response.getBody().getNome());
    }
}