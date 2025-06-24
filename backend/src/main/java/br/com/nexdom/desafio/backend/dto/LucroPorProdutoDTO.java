package br.com.nexdom.desafio.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para o relatório de lucro por produto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para o relatório de lucro por produto")
public class LucroPorProdutoDTO {

    @Schema(description = "ID do produto", example = "1")
    private Long id;

    @Schema(description = "Descrição do produto", example = "Smartphone Samsung Galaxy S21")
    private String descricao;

    @Schema(description = "Total de unidades vendidas", example = "15")
    private Integer totalUnidadesVendidas;

    @Schema(description = "Lucro total", example = "7500.00")
    private BigDecimal lucroTotal;
}