package br.com.vortex.application.repository;

import br.com.vortex.application.model.TipoProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository para a entidade TipoProduto.
 */
@Repository
public interface TipoProdutoRepository extends JpaRepository<TipoProduto, Long> {
    
    /**
     * Busca um tipo de produto pelo nome.
     * 
     * @param nome Nome do tipo de produto
     * @return Optional contendo o tipo de produto, se encontrado
     */
    Optional<TipoProduto> findByNome(String nome);
    
    /**
     * Verifica se existe um tipo de produto com o nome informado.
     * 
     * @param nome Nome do tipo de produto
     * @return true se existir, false caso contrário
     */
    boolean existsByNome(String nome);
}