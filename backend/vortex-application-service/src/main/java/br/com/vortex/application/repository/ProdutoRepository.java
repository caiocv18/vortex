package br.com.vortex.application.repository;

import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.TipoProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository para a entidade Produto.
 */
@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    
    /**
     * Busca produtos por tipo de produto.
     * 
     * @param tipoProduto Tipo de produto
     * @return Lista de produtos do tipo informado
     */
    List<Produto> findByTipoProduto(TipoProduto tipoProduto);
    
    /**
     * Busca produtos por ID do tipo de produto.
     * 
     * @param tipoProdutoId ID do tipo de produto
     * @return Lista de produtos do tipo informado
     */
    List<Produto> findByTipoProdutoId(Long tipoProdutoId);
    
    /**
     * Verifica se existem produtos associados a um tipo de produto.
     * 
     * @param tipoProduto Tipo de produto
     * @return true se existirem produtos associados, false caso contrário
     */
    boolean existsByTipoProduto(TipoProduto tipoProduto);
}