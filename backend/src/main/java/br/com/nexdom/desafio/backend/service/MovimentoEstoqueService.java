package br.com.nexdom.desafio.backend.service;

import br.com.nexdom.desafio.backend.dto.MovimentoEstoqueDTO;
import br.com.nexdom.desafio.backend.exception.EstoqueInsuficienteException;
import br.com.nexdom.desafio.backend.exception.ResourceNotFoundException;
import br.com.nexdom.desafio.backend.model.MovimentoEstoque;
import br.com.nexdom.desafio.backend.model.Produto;
import br.com.nexdom.desafio.backend.model.enums.TipoMovimentacao;
import br.com.nexdom.desafio.backend.repository.MovimentoEstoqueRepository;
import br.com.nexdom.desafio.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço para operações relacionadas a MovimentoEstoque.
 */
@Service
public class MovimentoEstoqueService {

    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final ProdutoRepository produtoRepository;

    @Autowired
    public MovimentoEstoqueService(MovimentoEstoqueRepository movimentoEstoqueRepository,
                                  ProdutoRepository produtoRepository) {
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
        this.produtoRepository = produtoRepository;
    }

    /**
     * Cria um novo movimento de estoque.
     *
     * @param movimentoEstoqueDTO DTO com os dados do movimento de estoque
     * @return DTO do movimento de estoque criado
     * @throws ResourceNotFoundException se o produto não for encontrado
     * @throws EstoqueInsuficienteException se não houver estoque suficiente para uma saída
     */
    @Transactional
    public MovimentoEstoqueDTO criar(MovimentoEstoqueDTO movimentoEstoqueDTO) {
        Produto produto = produtoRepository.findById(movimentoEstoqueDTO.getProdutoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", movimentoEstoqueDTO.getProdutoId()));
        
        MovimentoEstoque movimentoEstoque = new MovimentoEstoque();
        movimentoEstoque.setDataMovimento(LocalDateTime.now());
        movimentoEstoque.setTipoMovimentacao(movimentoEstoqueDTO.getTipoMovimentacao());
        movimentoEstoque.setQuantidadeMovimentada(movimentoEstoqueDTO.getQuantidadeMovimentada());
        movimentoEstoque.setProduto(produto);
        
        // Lógica específica para cada tipo de movimentação
        if (movimentoEstoqueDTO.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
            // Para ENTRADA, incrementa o estoque e não define valor de venda
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + movimentoEstoqueDTO.getQuantidadeMovimentada());
            movimentoEstoque.setValorVenda(null);
        } else if (movimentoEstoqueDTO.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
            // Para SAIDA, verifica se há estoque suficiente
            if (produto.getQuantidadeEmEstoque() < movimentoEstoqueDTO.getQuantidadeMovimentada()) {
                throw new EstoqueInsuficienteException(
                        produto.getId(), 
                        produto.getQuantidadeEmEstoque(), 
                        movimentoEstoqueDTO.getQuantidadeMovimentada());
            }
            
            // Decrementa o estoque
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - movimentoEstoqueDTO.getQuantidadeMovimentada());
            
            // Calcula o valor de venda como valorFornecedor * 1.35
            BigDecimal valorVenda = produto.getValorFornecedor()
                    .multiply(new BigDecimal("1.35"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            movimentoEstoque.setValorVenda(valorVenda);
        }
        
        // Salva o produto atualizado
        produtoRepository.save(produto);
        
        // Salva o movimento de estoque
        MovimentoEstoque savedMovimento = movimentoEstoqueRepository.save(movimentoEstoque);
        
        return mapToDTO(savedMovimento);
    }

    /**
     * Busca todos os movimentos de estoque.
     *
     * @return Lista de DTOs dos movimentos de estoque
     */
    @Transactional(readOnly = true)
    public List<MovimentoEstoqueDTO> buscarTodos() {
        List<MovimentoEstoque> movimentos = movimentoEstoqueRepository.findAll();
        
        return movimentos.stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Busca um movimento de estoque pelo ID.
     *
     * @param id ID do movimento de estoque
     * @return DTO do movimento de estoque
     * @throws ResourceNotFoundException se o movimento de estoque não for encontrado
     */
    @Transactional(readOnly = true)
    public MovimentoEstoqueDTO buscarPorId(Long id) {
        MovimentoEstoque movimentoEstoque = movimentoEstoqueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MovimentoEstoque", "id", id));
        
        return mapToDTO(movimentoEstoque);
    }

    /**
     * Atualiza um movimento de estoque.
     * Atenção: Esta operação é complexa e deve garantir a consistência do estoque.
     *
     * @param id ID do movimento de estoque
     * @param movimentoEstoqueDTO DTO com os novos dados do movimento de estoque
     * @return DTO do movimento de estoque atualizado
     * @throws ResourceNotFoundException se o movimento de estoque ou o produto não for encontrado
     * @throws EstoqueInsuficienteException se não houver estoque suficiente para uma saída
     */
    @Transactional
    public MovimentoEstoqueDTO atualizar(Long id, MovimentoEstoqueDTO movimentoEstoqueDTO) {
        MovimentoEstoque movimentoEstoque = movimentoEstoqueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MovimentoEstoque", "id", id));
        
        Produto produto = produtoRepository.findById(movimentoEstoqueDTO.getProdutoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", movimentoEstoqueDTO.getProdutoId()));
        
        // Reverte o efeito do movimento original no estoque
        if (movimentoEstoque.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - movimentoEstoque.getQuantidadeMovimentada());
        } else if (movimentoEstoque.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + movimentoEstoque.getQuantidadeMovimentada());
        }
        
        // Aplica o efeito do novo movimento no estoque
        if (movimentoEstoqueDTO.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + movimentoEstoqueDTO.getQuantidadeMovimentada());
            movimentoEstoque.setValorVenda(null);
        } else if (movimentoEstoqueDTO.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
            // Verifica se há estoque suficiente
            if (produto.getQuantidadeEmEstoque() < movimentoEstoqueDTO.getQuantidadeMovimentada()) {
                throw new EstoqueInsuficienteException(
                        produto.getId(), 
                        produto.getQuantidadeEmEstoque(), 
                        movimentoEstoqueDTO.getQuantidadeMovimentada());
            }
            
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - movimentoEstoqueDTO.getQuantidadeMovimentada());
            
            // Calcula o valor de venda como valorFornecedor * 1.35
            BigDecimal valorVenda = produto.getValorFornecedor()
                    .multiply(new BigDecimal("1.35"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            movimentoEstoque.setValorVenda(valorVenda);
        }
        
        // Atualiza os dados do movimento
        movimentoEstoque.setTipoMovimentacao(movimentoEstoqueDTO.getTipoMovimentacao());
        movimentoEstoque.setQuantidadeMovimentada(movimentoEstoqueDTO.getQuantidadeMovimentada());
        movimentoEstoque.setProduto(produto);
        
        // Salva o produto atualizado
        produtoRepository.save(produto);
        
        // Salva o movimento de estoque
        MovimentoEstoque updatedMovimento = movimentoEstoqueRepository.save(movimentoEstoque);
        
        return mapToDTO(updatedMovimento);
    }

    /**
     * Exclui um movimento de estoque.
     * Atenção: Esta operação é complexa e deve garantir a consistência do estoque.
     *
     * @param id ID do movimento de estoque
     * @throws ResourceNotFoundException se o movimento de estoque não for encontrado
     */
    @Transactional
    public void excluir(Long id) {
        MovimentoEstoque movimentoEstoque = movimentoEstoqueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MovimentoEstoque", "id", id));
        
        Produto produto = movimentoEstoque.getProduto();
        
        // Reverte o efeito do movimento no estoque
        if (movimentoEstoque.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - movimentoEstoque.getQuantidadeMovimentada());
        } else if (movimentoEstoque.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + movimentoEstoque.getQuantidadeMovimentada());
        }
        
        // Salva o produto atualizado
        produtoRepository.save(produto);
        
        // Exclui o movimento de estoque
        movimentoEstoqueRepository.delete(movimentoEstoque);
    }

    /**
     * Converte uma entidade MovimentoEstoque para um DTO.
     *
     * @param movimentoEstoque Entidade MovimentoEstoque
     * @return DTO do movimento de estoque
     */
    private MovimentoEstoqueDTO mapToDTO(MovimentoEstoque movimentoEstoque) {
        MovimentoEstoqueDTO movimentoEstoqueDTO = new MovimentoEstoqueDTO();
        movimentoEstoqueDTO.setId(movimentoEstoque.getId());
        movimentoEstoqueDTO.setDataMovimento(movimentoEstoque.getDataMovimento());
        movimentoEstoqueDTO.setTipoMovimentacao(movimentoEstoque.getTipoMovimentacao());
        movimentoEstoqueDTO.setQuantidadeMovimentada(movimentoEstoque.getQuantidadeMovimentada());
        movimentoEstoqueDTO.setValorVenda(movimentoEstoque.getValorVenda());
        movimentoEstoqueDTO.setProdutoId(movimentoEstoque.getProduto().getId());
        
        return movimentoEstoqueDTO;
    }
}