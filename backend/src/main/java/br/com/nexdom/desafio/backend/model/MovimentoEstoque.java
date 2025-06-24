package br.com.nexdom.desafio.backend.model;

import br.com.nexdom.desafio.backend.model.enums.TipoMovimentacao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade que representa um movimento de estoque.
 */
@Entity
@Table(name = "movimento_estoque")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "movimento_estoque_seq")
    @SequenceGenerator(name = "movimento_estoque_seq", sequenceName = "movimento_estoque_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime dataMovimento = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "O tipo de movimentação é obrigatório")
    private TipoMovimentacao tipoMovimentacao;

    @Column(nullable = false)
    @NotNull(message = "A quantidade movimentada é obrigatória")
    @Positive(message = "A quantidade movimentada deve ser maior que zero")
    private Integer quantidadeMovimentada;

    @Column
    private BigDecimal valorVenda;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    @NotNull(message = "O produto é obrigatório")
    private Produto produto;
}