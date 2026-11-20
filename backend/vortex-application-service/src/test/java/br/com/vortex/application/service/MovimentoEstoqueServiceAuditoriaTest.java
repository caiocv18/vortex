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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A auditoria do movimento sai com a entidade {@code MovimentoEstoque}, tanto no caminho síncrono (message broker)
 * quanto no assíncrono (SQS).
 */
class MovimentoEstoqueServiceAuditoriaTest {

    private static final long PRODUTO_ID = 7L;

    private MovimentoEstoqueRepository movimentoRepository;
    private ProdutoRepository produtoRepository;
    private MessageBrokerService broker;

    @BeforeEach
    void setUp() {
        movimentoRepository = mock(MovimentoEstoqueRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        broker = mock(MessageBrokerService.class);
        when(produtoRepository.findById(PRODUTO_ID)).thenReturn(Optional.of(produto()));
        when(movimentoRepository.save(any(MovimentoEstoque.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void synchronousMovementIsAuditedThroughTheMessageBroker() {
        when(broker.isAvailable()).thenReturn(true);
        MovimentoEstoqueService service = new MovimentoEstoqueService(movimentoRepository, produtoRepository,
                new StaticListableBeanFactory().getBeanProvider(SqsProducerService.class), broker, false);

        service.criar(entrada(), "alice.estoque");

        verify(broker).publicarAuditoria(eq("MOVIMENTO_CRIADO"), eq("MovimentoEstoque"), any(), anyString(),
                eq("alice.estoque"), eq("SUCCESS"), isNull());
    }

    @Test
    void asynchronousMovementIsAuditedThroughSqs() {
        SqsProducerService sqs = mock(SqsProducerService.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("sqsProducerService", sqs);
        MovimentoEstoqueService service = new MovimentoEstoqueService(movimentoRepository, produtoRepository,
                beans.getBeanProvider(SqsProducerService.class), broker, true);

        service.criar(entrada(), "alice.estoque");

        verify(sqs).enviarAuditoria(eq("MOVIMENTO_ENVIADO_SQS"), eq("MovimentoEstoque"), isNull(),
                eq("alice.estoque"), anyString());
    }

    private static MovimentoEstoqueDTO entrada() {
        MovimentoEstoqueDTO dto = new MovimentoEstoqueDTO();
        dto.setProdutoId(PRODUTO_ID);
        dto.setTipoMovimentacao(TipoMovimentacao.ENTRADA);
        dto.setQuantidadeMovimentada(10);
        return dto;
    }

    private static Produto produto() {
        TipoProduto tipo = new TipoProduto();
        tipo.setId(1L);
        tipo.setNome("Papelaria");
        Produto produto = new Produto();
        produto.setId(PRODUTO_ID);
        produto.setDescricao("Caneta azul");
        produto.setValorFornecedor(new BigDecimal("1.20"));
        produto.setQuantidadeEmEstoque(60);
        produto.setTipoProduto(tipo);
        return produto;
    }
}
