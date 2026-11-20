package br.com.vortex.application.service;

import br.com.vortex.application.dto.MovimentoEstoqueDTO;
import br.com.vortex.application.dto.ProdutoDTO;
import br.com.vortex.application.dto.TipoProdutoDTO;
import br.com.vortex.application.exception.ResourceNotFoundException;
import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import br.com.vortex.application.repository.TipoProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A mensagem do 404 nomeia o recurso que faltou: é ela que o GlobalExceptionHandler devolve no corpo de erro.
 */
class ResourceNotFoundMessagesTest {

    private static final long ID = 9L;

    private MovimentoEstoqueRepository movimentoRepository;
    private ProdutoRepository produtoRepository;
    private TipoProdutoRepository tipoProdutoRepository;

    @BeforeEach
    void setUp() {
        movimentoRepository = mock(MovimentoEstoqueRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        tipoProdutoRepository = mock(TipoProdutoRepository.class);
        when(movimentoRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(produtoRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(tipoProdutoRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void movimentoEstoqueServiceNamesTheMissingMovementOrProduct() {
        MovimentoEstoqueService service = new MovimentoEstoqueService(movimentoRepository, produtoRepository,
                new StaticListableBeanFactory().getBeanProvider(SqsProducerService.class),
                mock(MessageBrokerService.class), false);
        MovimentoEstoqueDTO dto = movimentoDto();

        assertNotFound("Produto", () -> service.criar(dto, "alice"));
        assertNotFound("MovimentoEstoque", () -> service.buscarPorId(ID));
        assertNotFound("MovimentoEstoque", () -> service.atualizar(ID, dto));
        assertNotFound("MovimentoEstoque", () -> service.excluir(ID));

        // Movimento existe, produto do DTO não
        MovimentoEstoque existente = new MovimentoEstoque();
        existente.setTipoMovimentacao(TipoMovimentacao.ENTRADA);
        existente.setQuantidadeMovimentada(1);
        when(movimentoRepository.findById(ID)).thenReturn(Optional.of(existente));
        assertNotFound("Produto", () -> service.atualizar(ID, dto));
    }

    @Test
    void produtoServiceNamesTheMissingProduct() {
        ProdutoService service = new ProdutoService(produtoRepository, tipoProdutoRepository, movimentoRepository);
        ProdutoDTO dto = new ProdutoDTO();
        dto.setTipoProdutoId(1L);

        assertNotFound("Produto", () -> service.buscarPorId(ID));
        assertNotFound("Produto", () -> service.atualizar(ID, dto));
        assertNotFound("Produto", () -> service.excluir(ID));
    }

    @Test
    void tipoProdutoServiceNamesTheMissingProductType() {
        TipoProdutoService service = new TipoProdutoService(tipoProdutoRepository, produtoRepository);
        TipoProdutoDTO dto = new TipoProdutoDTO();
        dto.setNome("Papelaria");

        assertNotFound("TipoProduto", () -> service.buscarPorId(ID));
        assertNotFound("TipoProduto", () -> service.atualizar(ID, dto));
        assertNotFound("TipoProduto", () -> service.excluir(ID));
    }

    private static void assertNotFound(String recurso, Executable call) {
        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class, call);
        assertEquals(recurso + " não encontrado com id: '" + ID + "'", failure.getMessage());
    }

    private static MovimentoEstoqueDTO movimentoDto() {
        MovimentoEstoqueDTO dto = new MovimentoEstoqueDTO();
        dto.setProdutoId(ID);
        dto.setTipoMovimentacao(TipoMovimentacao.ENTRADA);
        dto.setQuantidadeMovimentada(10);
        return dto;
    }
}
