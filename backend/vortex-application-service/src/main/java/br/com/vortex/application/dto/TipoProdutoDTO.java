package br.com.vortex.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para transferência de dados de TipoProduto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para transferência de dados de Tipo de Produto")
public class TipoProdutoDTO {

    @Schema(description = "ID do tipo de produto", example = "1")
    private Long id;

    @NotBlank(message = "O nome do tipo de produto é obrigatório")
    @Schema(description = "Nome do tipo de produto", example = "Eletrônicos", required = true)
    private String nome;
}