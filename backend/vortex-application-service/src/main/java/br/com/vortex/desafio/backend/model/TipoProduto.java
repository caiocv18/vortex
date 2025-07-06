package br.com.vortex.desafio.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidade que representa um tipo de produto.
 */
@Entity
@Table(name = "tipo_produto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TipoProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tipo_produto_seq")
    @SequenceGenerator(name = "tipo_produto_seq", sequenceName = "tipo_produto_seq", allocationSize = 1)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "O nome do tipo de produto é obrigatório")
    private String nome;
}