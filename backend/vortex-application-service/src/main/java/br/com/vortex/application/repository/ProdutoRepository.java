package br.com.vortex.application.repository;

import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.TipoProduto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository para a entidade Produto.
 */
@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /**
     * Uma página de produtos, com o tipo de produto no mesmo SELECT: sem o grafo, cada tipo distinto da
     * página custaria um SELECT a mais (o {@code @ManyToOne} é EAGER).
     *
     * @param pageable página, tamanho e ordenação
     * @return Página de produtos
     */
    @Override
    @EntityGraph(attributePaths = "tipoProduto")
    Page<Produto> findAll(Pageable pageable);

    /**
     * Uma página dos produtos cuja descrição contém o trecho informado, sem diferenciar maiúsculas. Os curingas
     * do LIKE ({@code %} e {@code _}) no trecho valem como texto, porque a consulta derivada os escapa.
     *
     * @param descricao trecho da descrição
     * @param pageable página, tamanho e ordenação
     * @return Página de produtos
     */
    @EntityGraph(attributePaths = "tipoProduto")
    Page<Produto> findByDescricaoContainingIgnoreCase(String descricao, Pageable pageable);

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
