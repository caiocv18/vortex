package br.com.nexdom.desafio.backend.dto;

import br.com.nexdom.desafio.backend.model.enums.TipoMovimentacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para transferência de dados de MovimentoEstoque.
 * Utilizado para criar, atualizar e retornar informações sobre movimentos de estoque.
 * Os movimentos podem ser de entrada (incrementa o estoque) ou saída (decrementa o estoque).
 * Para movimentos de saída, o valor de venda é calculado automaticamente como 1.35 * valorFornecedor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description = "Representa um movimento de estoque (entrada ou saída)",
    name = "MovimentoEstoque",
    title = "Movimento de Estoque"
)
public class MovimentoEstoqueDTO {

    @Schema(
        description = "Identificador único do movimento de estoque (gerado automaticamente)",
        example = "1",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long id;

    @Schema(
        description = "Data e hora em que o movimento foi registrado (gerado automaticamente)",
        example = "2023-01-01T10:00:00",
        format = "date-time",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime dataMovimento;

    @NotNull(message = "O tipo de movimentação é obrigatório")
    @Schema(
        description = "Tipo de movimentação (ENTRADA aumenta o estoque, SAIDA diminui o estoque)",
        example = "ENTRADA",
        required = true,
        allowableValues = {"ENTRADA", "SAIDA"}
    )
    private TipoMovimentacao tipoMovimentacao;

    @NotNull(message = "A quantidade movimentada é obrigatória")
    @Positive(message = "A quantidade movimentada deve ser maior que zero")
    @Schema(
        description = "Quantidade de itens movimentados (deve ser maior que zero)",
        example = "5",
        required = true,
        minimum = "1"
    )
    private Integer quantidadeMovimentada;

    @Schema(
        description = "Valor de venda unitário (calculado automaticamente para SAIDA como 1.35 * valorFornecedor, nulo para ENTRADA)",
        example = "2025.00",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    private BigDecimal valorVenda;

    @NotNull(message = "O produto é obrigatório")
    @Schema(
        description = "Identificador do produto associado ao movimento",
        example = "1",
        required = true
    )
    private Long produtoId;
}
