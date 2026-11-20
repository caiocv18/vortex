package br.com.vortex.application.service;

import br.com.vortex.application.dto.ProdutoDTO;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.TipoProduto;
import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import br.com.vortex.application.repository.TipoProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A busca paginada de produtos escolhe a consulta pelo {@code q}: sem trecho, a página de todos; com trecho, o
 * filtro pela descrição, com o trecho sem os espaços das pontas. A página e a ordenação pedidas chegam intactas ao
 * repositório, e o total da página vem dele.
 */
class ProdutoServiceBuscaTest {

    private static final Pageable PAGINA = PageRequest.of(1, 5, Sort.by("descricao"));
    private static final Pageable ESTAVEL = Paginacao.comDesempatePorId(PAGINA);

    private ProdutoRepository produtoRepository;
    private ProdutoService service;

    @BeforeEach
    void setUp() {
        produtoRepository = mock(ProdutoRepository.class);
        service = new ProdutoService(produtoRepository, mock(TipoProdutoRepository.class),
                mock(MovimentoEstoqueRepository.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void semTrechoBuscaTodos(String q) {
        when(produtoRepository.findAll(ESTAVEL)).thenReturn(new PageImpl<>(List.of(produto()), PAGINA, 6));

        Page<ProdutoDTO> pagina = service.buscar(q, PAGINA);

        assertEquals(6, pagina.getTotalElements());
        assertEquals(PAGINA, pagina.getPageable());
        verify(produtoRepository, never()).findByDescricaoContainingIgnoreCase(anyString(), any(Pageable.class));
    }

    @Test
    void comTrechoFiltraPelaDescricaoSemOsEspacosDasPontas() {
        when(produtoRepository.findByDescricaoContainingIgnoreCase("Caneta", ESTAVEL))
                .thenReturn(new PageImpl<>(List.of(produto()), PAGINA, 6));

        Page<ProdutoDTO> pagina = service.buscar("  Caneta ", PAGINA);

        ProdutoDTO dto = pagina.getContent().getFirst();
        assertEquals(42L, dto.getId());
        assertEquals("Caneta azul", dto.getDescricao());
        assertEquals(new BigDecimal("1.20"), dto.getValorFornecedor());
        assertEquals(60, dto.getQuantidadeEmEstoque());
        assertEquals(3L, dto.getTipoProdutoId());
        verify(produtoRepository, never()).findAll(any(Pageable.class));
    }

    private static Produto produto() {
        TipoProduto tipo = new TipoProduto();
        tipo.setId(3L);
        tipo.setNome("Papelaria");
        Produto produto = new Produto();
        produto.setId(42L);
        produto.setDescricao("Caneta azul");
        produto.setValorFornecedor(new BigDecimal("1.20"));
        produto.setQuantidadeEmEstoque(60);
        produto.setTipoProduto(tipo);
        return produto;
    }
}
