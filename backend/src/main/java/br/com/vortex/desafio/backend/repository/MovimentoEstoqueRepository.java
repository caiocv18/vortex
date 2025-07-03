package br.com.vortex.desafio.backend.repository;

import br.com.vortex.desafio.backend.model.MovimentoEstoque;
import br.com.vortex.desafio.backend.model.Produto;
import br.com.vortex.desafio.backend.model.enums.TipoMovimentacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository para a entidade MovimentoEstoque.
 */
@Repository
public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {
    
    /**
     * Busca movimentos de estoque por produto.
     * 
     * @param produto Produto
     * @return Lista de movimentos de estoque do produto informado
     */
    List<MovimentoEstoque> findByProduto(Produto produto);
    
    /**
     * Busca movimentos de estoque por produto e tipo de movimentação.
     * 
     * @param produto Produto
     * @param tipoMovimentacao Tipo de movimentação
     * @return Lista de movimentos de estoque do produto e tipo informados
     */
    List<MovimentoEstoque> findByProdutoAndTipoMovimentacao(Produto produto, TipoMovimentacao tipoMovimentacao);
    
    /**
     * Busca movimentos de estoque por ID do produto.
     * 
     * @param produtoId ID do produto
     * @return Lista de movimentos de estoque do produto informado
     */
    List<MovimentoEstoque> findByProdutoId(Long produtoId);
    
    /**
     * Verifica se existem movimentos de estoque associados a um produto.
     * 
     * @param produto Produto
     * @return true se existirem movimentos associados, false caso contrário
     */
    boolean existsByProduto(Produto produto);
    
    /**
     * Conta o número de saídas de um produto.
     * 
     * @param produto Produto
     * @return Número de saídas do produto
     */
    long countByProdutoAndTipoMovimentacao(Produto produto, TipoMovimentacao tipoMovimentacao);
    
    /**
     * Calcula o total de unidades vendidas de um produto.
     * 
     * @param produtoId ID do produto
     * @return Total de unidades vendidas
     */
    @Query("SELECT SUM(m.quantidadeMovimentada) FROM MovimentoEstoque m WHERE m.produto.id = :produtoId AND m.tipoMovimentacao = 'SAIDA'")
    Integer getTotalUnidadesVendidas(@Param("produtoId") Long produtoId);
    
    /**
     * Calcula o lucro total de um produto.
     * 
     * @param produtoId ID do produto
     * @return Lucro total
     */
    @Query("SELECT SUM(m.valorVenda * m.quantidadeMovimentada) - SUM(m.produto.valorFornecedor * m.quantidadeMovimentada) FROM MovimentoEstoque m WHERE m.produto.id = :produtoId AND m.tipoMovimentacao = 'SAIDA'")
    BigDecimal getLucroTotal(@Param("produtoId") Long produtoId);
}