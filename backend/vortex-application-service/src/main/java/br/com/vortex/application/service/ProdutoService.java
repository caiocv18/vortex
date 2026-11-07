package br.com.vortex.application.service;

import br.com.vortex.application.dto.ProdutoDTO;
import br.com.vortex.application.exception.ResourceNotFoundException;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.TipoProduto;
import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import br.com.vortex.application.repository.TipoProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serviço para operações relacionadas a Produto.
 */
@Service
@Slf4j
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final TipoProdutoRepository tipoProdutoRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;

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
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoDTO criar(ProdutoDTO produtoDTO) {
        log.debug("Iniciando criação de produto: {}", produtoDTO.getDescricao());
        
        TipoProduto tipoProduto = tipoProdutoRepository.findById(produtoDTO.getTipoProdutoId())
                .orElseThrow(() -> {
                    log.warn("Tipo de produto não encontrado: ID {}", produtoDTO.getTipoProdutoId());
                    return new ResourceNotFoundException("TipoProduto", "id", produtoDTO.getTipoProdutoId());
                });
        
        Produto produto = new Produto();
        produto.setDescricao(produtoDTO.getDescricao());
        produto.setValorFornecedor(produtoDTO.getValorFornecedor());
        produto.setQuantidadeEmEstoque(produtoDTO.getQuantidadeEmEstoque() != null ? 
                produtoDTO.getQuantidadeEmEstoque() : 0);
        produto.setTipoProduto(tipoProduto);
        
        Produto savedProduto = produtoRepository.save(produto);
        
        log.info("Produto criado com sucesso - ID: {}, Descrição: {}", 
                savedProduto.getId(), savedProduto.getDescricao());
        
        return mapToDTO(savedProduto);
    }

    /**
     * Busca todos os produtos com paginação.
     *
     * @param pageable Configurações de paginação
     * @return Página de DTOs dos produtos
     */
    @Transactional(readOnly = true)
    public Page<ProdutoDTO> buscarTodos(Pageable pageable) {
        Page<Produto> produtos = produtoRepository.findAll(pageable);
        
        return produtos.map(this::mapToDTO);
    }

    /**
     * Busca todos os produtos (método legado sem paginação - use com cuidado).
     * 
     * @deprecated Use buscarTodos(Pageable) para melhor performance
     * @return Lista de DTOs dos produtos (limitada a 1000 itens)
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<ProdutoDTO> buscarTodos() {
        Pageable pageable = PageRequest.of(0, 1000, Sort.by("id"));
        Page<Produto> produtos = produtoRepository.findAll(pageable);
        
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
    @Cacheable(value = "produtos", key = "#id")
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
    @CacheEvict(value = {"produtos", "produto-types"}, key = "#id")
    public ProdutoDTO atualizar(Long id, ProdutoDTO produtoDTO) {
        log.debug("Iniciando atualização do produto ID: {}", id);
        
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Produto não encontrado para atualização: ID {}", id);
                    return new ResourceNotFoundException("Produto", "id", id);
                });
        
        TipoProduto tipoProduto = tipoProdutoRepository.findById(produtoDTO.getTipoProdutoId())
                .orElseThrow(() -> {
                    log.warn("Tipo de produto não encontrado: ID {}", produtoDTO.getTipoProdutoId());
                    return new ResourceNotFoundException("TipoProduto", "id", produtoDTO.getTipoProdutoId());
                });
        
        String descricaoAnterior = produto.getDescricao();
        produto.setDescricao(produtoDTO.getDescricao());
        produto.setValorFornecedor(produtoDTO.getValorFornecedor());
        produto.setQuantidadeEmEstoque(produtoDTO.getQuantidadeEmEstoque());
        produto.setTipoProduto(tipoProduto);
        
        Produto updatedProduto = produtoRepository.save(produto);
        
        log.info("Produto atualizado com sucesso - ID: {}, Descrição alterada de '{}' para '{}'", 
                id, descricaoAnterior, updatedProduto.getDescricao());
        
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
    @CacheEvict(value = {"produtos", "produto-types"}, key = "#id")
    public void excluir(Long id) {
        log.debug("Iniciando exclusão do produto ID: {}", id);
        
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Produto não encontrado para exclusão: ID {}", id);
                    return new ResourceNotFoundException("Produto", "id", id);
                });
        
        if (movimentoEstoqueRepository.existsByProduto(produto)) {
            log.warn("Tentativa de exclusão de produto com movimentos de estoque - ID: {}, Descrição: {}", 
                    id, produto.getDescricao());
            throw new DataIntegrityViolationException("Não é possível excluir um produto que possui movimentos de estoque associados");
        }
        
        String descricao = produto.getDescricao();
        produtoRepository.delete(produto);
        
        log.info("Produto excluído com sucesso - ID: {}, Descrição: '{}'", id, descricao);
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