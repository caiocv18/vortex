package br.com.vortex.application.service;

import br.com.vortex.application.dto.MovimentoEstoqueDTO;
import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.TipoProduto;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A busca paginada de movimentos repassa a página e a ordenação pedidas ao repositório, com ou sem filtro, e
 * devolve a página mapeada para DTO com o total do repositório. Os filtros em si (q e tipo contra o banco) são
 * provados no {@code MovimentoPaginacaoIntegrationTest}.
 */
class MovimentoEstoqueServiceBuscaTest {

    private static final Pageable PAGINA = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "dataMovimento"));
    private static final Pageable ESTAVEL = Paginacao.comDesempatePorId(PAGINA);

    private MovimentoEstoqueRepository movimentoRepository;
    private MovimentoEstoqueService service;

    @BeforeEach
    void setUp() {
        movimentoRepository = mock(MovimentoEstoqueRepository.class);
        service = new MovimentoEstoqueService(movimentoRepository, mock(ProdutoRepository.class),
                new StaticListableBeanFactory().getBeanProvider(SqsProducerService.class),
                mock(MessageBrokerService.class), false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void semFiltroRepassaAPaginaEMapeiaOsMovimentos() {
        when(movimentoRepository.findAll(any(Specification.class), eq(ESTAVEL)))
                .thenReturn(new PageImpl<>(List.of(saida()), PAGINA, 7));

        Page<MovimentoEstoqueDTO> pagina = service.buscar(null, null, PAGINA);

        assertEquals(7, pagina.getTotalElements());
        assertEquals(PAGINA, pagina.getPageable());
        MovimentoEstoqueDTO dto = pagina.getContent().getFirst();
        assertEquals(11L, dto.getId());
        assertEquals(TipoMovimentacao.SAIDA, dto.getTipoMovimentacao());
        assertEquals(2, dto.getQuantidadeMovimentada());
        assertEquals(new BigDecimal("1.62"), dto.getValorVenda());
        assertEquals(LocalDateTime.of(2024, 3, 1, 10, 0), dto.getDataMovimento());
        assertEquals(7L, dto.getProdutoId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void comFiltrosAindaUsaUmaConsultaSoComAPaginaPedida() {
        when(movimentoRepository.findAll(any(Specification.class), eq(ESTAVEL)))
                .thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

        assertEquals(0, service.buscar("  caneta ", TipoMovimentacao.SAIDA, PAGINA).getTotalElements());
        verify(movimentoRepository).findAll(any(Specification.class), eq(ESTAVEL));
    }

    private static MovimentoEstoque saida() {
        TipoProduto tipo = new TipoProduto();
        tipo.setId(1L);
        tipo.setNome("Papelaria");
        Produto produto = new Produto();
        produto.setId(7L);
        produto.setDescricao("Caneta azul");
        produto.setValorFornecedor(new BigDecimal("1.20"));
        produto.setQuantidadeEmEstoque(58);
        produto.setTipoProduto(tipo);
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setId(11L);
        movimento.setTipoMovimentacao(TipoMovimentacao.SAIDA);
        movimento.setQuantidadeMovimentada(2);
        movimento.setValorVenda(new BigDecimal("1.62"));
        movimento.setDataMovimento(LocalDateTime.of(2024, 3, 1, 10, 0));
        movimento.setProduto(produto);
        return movimento;
    }
}
