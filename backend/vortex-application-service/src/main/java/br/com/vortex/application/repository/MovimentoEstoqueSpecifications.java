package br.com.vortex.application.repository;

import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Filtros da lista paginada de movimentos de estoque, combinados com {@link Specification#and}. Cada um entra só
 * quando o parâmetro veio: um filtro ausente não vira {@code :param IS NULL} na consulta, que o PostgreSQL recusa
 * quando não consegue inferir o tipo do parâmetro nulo.
 */
public final class MovimentoEstoqueSpecifications {

    /** Escape do LIKE: o trecho pesquisado é texto, então {@code %} e {@code _} nele não são curingas. */
    static final char ESCAPE = '\\';

    private MovimentoEstoqueSpecifications() {
    }

    /**
     * Movimentos de um tipo só.
     *
     * @param tipo ENTRADA ou SAIDA
     * @return filtro pelo tipo de movimentação
     */
    public static Specification<MovimentoEstoque> doTipo(TipoMovimentacao tipo) {
        return (root, query, cb) -> cb.equal(root.get("tipoMovimentacao"), tipo);
    }

    /**
     * Movimentos cujo produto tem na descrição o trecho informado, sem diferenciar maiúsculas.
     *
     * @param trecho trecho da descrição do produto, já sem os espaços das pontas
     * @return filtro pela descrição do produto
     */
    public static Specification<MovimentoEstoque> comDescricaoDoProdutoContendo(String trecho) {
        String padrao = "%" + escaparCuringas(trecho.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) ->
                cb.like(cb.lower(root.<Produto>get("produto").<String>get("descricao")), padrao, ESCAPE);
    }

    static String escaparCuringas(String texto) {
        return texto.replace(String.valueOf(ESCAPE), String.valueOf(ESCAPE) + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
    }
}
