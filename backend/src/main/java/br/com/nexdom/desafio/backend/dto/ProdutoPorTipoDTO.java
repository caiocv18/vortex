package br.com.nexdom.desafio.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para o relatório de produtos por tipo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para o relatório de produtos por tipo")
public class ProdutoPorTipoDTO {

    @Schema(description = "ID do produto", example = "1")
    private Long id;

    @Schema(description = "Descrição do produto", example = "Smartphone Samsung Galaxy S21")
    private String descricao;

    @Schema(description = "Quantidade total de saídas", example = "15")
    private Integer totalSaidas;

    @Schema(description = "Quantidade em estoque", example = "10")
    private Integer quantidadeEmEstoque;
}