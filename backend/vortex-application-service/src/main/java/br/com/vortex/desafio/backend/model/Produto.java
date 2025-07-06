package br.com.vortex.desafio.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Entidade que representa um produto.
 */
@Entity
@Table(name = "produto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "produto_seq")
    @SequenceGenerator(name = "produto_seq", sequenceName = "produto_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "A descrição do produto é obrigatória")
    private String descricao;

    @Column(nullable = false)
    @NotNull(message = "O valor do fornecedor é obrigatório")
    @Positive(message = "O valor do fornecedor deve ser positivo")
    private BigDecimal valorFornecedor;

    @Column(nullable = false)
    @PositiveOrZero(message = "A quantidade em estoque não pode ser negativa")
    private Integer quantidadeEmEstoque = 0;

    @ManyToOne
    @JoinColumn(name = "tipo_produto_id", nullable = false)
    @NotNull(message = "O tipo de produto é obrigatório")
    private TipoProduto tipoProduto;
}