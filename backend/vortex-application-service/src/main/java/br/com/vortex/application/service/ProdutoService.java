package br.com.vortex.application.service;

import br.com.vortex.application.dto.ProdutoDTO;
import br.com.vortex.application.exception.ResourceNotFoundException;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.TipoProduto;
import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import br.com.vortex.application.repository.TipoProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serviço para operações relacionadas a Produto.
 */
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final TipoProdutoRepository tipoProdutoRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;

    @Autowired
    public ProdutoService(ProdutoRepository produtoRepository, 
                         TipoProdutoRepository tipoProdutoRepository,
                         MovimentoEstoqueRepository movimentoEstoqueRepository) {
        this.produtoRepository = produtoRepository;
        this.tipoProdutoRepository = tipoProdutoRepository;
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
    }

    /**
     * Cria um novo produto.
     *
     * @param produtoDTO DTO com os dados do produto
     * @return DTO do produto criado
     * @throws ResourceNotFoundException se o tipo de produto não for encontrado
     */
    @Transactional
    public ProdutoDTO criar(ProdutoDTO produtoDTO) {
        TipoProduto tipoProduto = tipoProdutoRepository.findById(produtoDTO.getTipoProdutoId())
                .orElseThrow(() -> new ResourceNotFoundException("TipoProduto", "id", produtoDTO.getTipoProdutoId()));
        
        Produto produto = new Produto();
        produto.setDescricao(produtoDTO.getDescricao());
        produto.setValorFornecedor(produtoDTO.getValorFornecedor());
        produto.setQuantidadeEmEstoque(produtoDTO.getQuantidadeEmEstoque() != null ? 
                produtoDTO.getQuantidadeEmEstoque() : 0);
        produto.setTipoProduto(tipoProduto);
        
        Produto savedProduto = produtoRepository.save(produto);
        
        return mapToDTO(savedProduto);
    }

    /**
     * Busca todos os produtos.
     *
     * @return Lista de DTOs dos produtos
     */
    @Transactional(readOnly = true)
    public List<ProdutoDTO> buscarTodos() {
        List<Produto> produtos = produtoRepository.findAll();
        
        return produtos.stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Busca um produto pelo ID.
     *
     * @param id ID do produto
     * @return DTO do produto
     * @throws ResourceNotFoundException se o produto não for encontrado
     */
    @Transactional(readOnly = true)
    public ProdutoDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", id));
        
        return mapToDTO(produto);
    }

    /**
     * Atualiza um produto.
     *
     * @param id ID do produto
     * @param produtoDTO DTO com os novos dados do produto
     * @return DTO do produto atualizado
     * @throws ResourceNotFoundException se o produto ou o tipo de produto não for encontrado
     */
    @Transactional
    public ProdutoDTO atualizar(Long id, ProdutoDTO produtoDTO) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", id));
        
        TipoProduto tipoProduto = tipoProdutoRepository.findById(produtoDTO.getTipoProdutoId())
                .orElseThrow(() -> new ResourceNotFoundException("TipoProduto", "id", produtoDTO.getTipoProdutoId()));
        
        produto.setDescricao(produtoDTO.getDescricao());
        produto.setValorFornecedor(produtoDTO.getValorFornecedor());
        produto.setQuantidadeEmEstoque(produtoDTO.getQuantidadeEmEstoque());
        produto.setTipoProduto(tipoProduto);
        
        Produto updatedProduto = produtoRepository.save(produto);
        
        return mapToDTO(updatedProduto);
    }

    /**
     * Exclui um produto.
     *
     * @param id ID do produto
     * @throws ResourceNotFoundException se o produto não for encontrado
     * @throws DataIntegrityViolationException se houver movimentos de estoque associados ao produto
     */
    @Transactional
    public void excluir(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", id));
        
        if (movimentoEstoqueRepository.existsByProduto(produto)) {
            throw new DataIntegrityViolationException("Não é possível excluir um produto que possui movimentos de estoque associados");
        }
        
        produtoRepository.delete(produto);
    }

    /**
     * Converte uma entidade Produto para um DTO.
     *
     * @param produto Entidade Produto
     * @return DTO do produto
     */
    private ProdutoDTO mapToDTO(Produto produto) {
        ProdutoDTO produtoDTO = new ProdutoDTO();
        produtoDTO.setId(produto.getId());
        produtoDTO.setDescricao(produto.getDescricao());
        produtoDTO.setValorFornecedor(produto.getValorFornecedor());
        produtoDTO.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque());
        produtoDTO.setTipoProdutoId(produto.getTipoProduto().getId());
        
        return produtoDTO;
    }
}