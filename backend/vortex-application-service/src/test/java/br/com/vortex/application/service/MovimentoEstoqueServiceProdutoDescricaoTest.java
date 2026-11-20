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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * O {@code produtoDescricao} do DTO de movimento é só de saída: vem do produto do movimento em toda resposta
 * (consulta, criação síncrona e assíncrona, alteração) e o valor que o cliente manda é ignorado, sem tocar a
 * descrição do produto.
 */
class MovimentoEstoqueServiceProdutoDescricaoTest {

    private static final long PRODUTO_ID = 7L;
    private static final String DESCRICAO = "Caneta azul";
    private static final String FORJADA = "Descrição que o cliente tentou impor";

    private MovimentoEstoqueRepository movimentoRepository;
    private ProdutoRepository produtoRepository;
    private Produto produto;

    @BeforeEach
    void setUp() {
        movimentoRepository = mock(MovimentoEstoqueRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        produto = produto();
        when(produtoRepository.findById(PRODUTO_ID)).thenReturn(Optional.of(produto));
        when(movimentoRepository.save(any(MovimentoEstoque.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void consultaTrazADescricaoDoProduto() {
        when(movimentoRepository.findById(3L)).thenReturn(Optional.of(movimento(3L)));

        assertEquals(DESCRICAO, sincrono().buscarPorId(3L).getProdutoDescricao());
    }

    @Test
    void criacaoIgnoraADescricaoRecebida() {
        MovimentoEstoqueDTO criado = sincrono().criar(entradaComDescricaoForjada(), "alice.estoque");

        assertEquals(DESCRICAO, criado.getProdutoDescricao());
        assertEquals(DESCRICAO, produto.getDescricao());
    }

    @Test
    void criacaoAssincronaTambemDevolveADescricaoDoProduto() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("sqsProducerService", mock(SqsProducerService.class));
        MovimentoEstoqueService service = new MovimentoEstoqueService(movimentoRepository, produtoRepository,
                beans.getBeanProvider(SqsProducerService.class), mock(MessageBrokerService.class), true);

        assertEquals(DESCRICAO, service.criar(entradaComDescricaoForjada(), "alice.estoque").getProdutoDescricao());
    }

    @Test
    void alteracaoIgnoraADescricaoRecebida() {
        when(movimentoRepository.findById(3L)).thenReturn(Optional.of(movimento(3L)));

        MovimentoEstoqueDTO alterado = sincrono().atualizar(3L, entradaComDescricaoForjada());

        assertEquals(DESCRICAO, alterado.getProdutoDescricao());
        assertEquals(DESCRICAO, produto.getDescricao());
    }

    private MovimentoEstoqueService sincrono() {
        return new MovimentoEstoqueService(movimentoRepository, produtoRepository,
                new StaticListableBeanFactory().getBeanProvider(SqsProducerService.class),
                mock(MessageBrokerService.class), false);
    }

    private static MovimentoEstoqueDTO entradaComDescricaoForjada() {
        MovimentoEstoqueDTO dto = new MovimentoEstoqueDTO();
        dto.setProdutoId(PRODUTO_ID);
        dto.setTipoMovimentacao(TipoMovimentacao.ENTRADA);
        dto.setQuantidadeMovimentada(4);
        dto.setProdutoDescricao(FORJADA);
        return dto;
    }

    private MovimentoEstoque movimento(long id) {
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setId(id);
        movimento.setTipoMovimentacao(TipoMovimentacao.ENTRADA);
        movimento.setQuantidadeMovimentada(2);
        movimento.setDataMovimento(LocalDateTime.of(2024, 3, 1, 10, 0));
        movimento.setProduto(produto);
        return movimento;
    }

    private static Produto produto() {
        TipoProduto tipo = new TipoProduto();
        tipo.setId(1L);
        tipo.setNome("Papelaria");
        Produto produto = new Produto();
        produto.setId(PRODUTO_ID);
        produto.setDescricao(DESCRICAO);
        produto.setValorFornecedor(new BigDecimal("1.20"));
        produto.setQuantidadeEmEstoque(60);
        produto.setTipoProduto(tipo);
        return produto;
    }
}
