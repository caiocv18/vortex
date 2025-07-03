package br.com.vortex.desafio.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para transferência de dados de Produto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para transferência de dados de Produto")
public class ProdutoDTO {

    @Schema(description = "ID do produto", example = "1")
    private Long id;

    @NotBlank(message = "A descrição do produto é obrigatória")
    @Schema(description = "Descrição do produto", example = "Smartphone Samsung Galaxy S21", required = true)
    private String descricao;

    @NotNull(message = "O valor do fornecedor é obrigatório")
    @Positive(message = "O valor do fornecedor deve ser positivo")
    @Schema(description = "Valor do fornecedor", example = "1500.00", required = true)
    private BigDecimal valorFornecedor;

    @PositiveOrZero(message = "A quantidade em estoque não pode ser negativa")
    @Schema(description = "Quantidade em estoque", example = "10", defaultValue = "0")
    private Integer quantidadeEmEstoque;

    @NotNull(message = "O tipo de produto é obrigatório")
    @Schema(description = "ID do tipo de produto", example = "1", required = true)
    private Long tipoProdutoId;
}