package br.com.nexdom.desafio.backend.controller;

import br.com.nexdom.desafio.backend.dto.LucroPorProdutoDTO;
import br.com.nexdom.desafio.backend.dto.ProdutoPorTipoDTO;
import br.com.nexdom.desafio.backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller para operações relacionadas a relatórios.
 */
@RestController
@RequestMapping("/api/relatorios")
@Tag(name = "Relatórios", description = "API para geração de relatórios")
public class RelatorioController {

    private final RelatorioService relatorioService;

    @Autowired
    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    /**
     * Gera relatório de produtos por tipo.
     *
     * @param tipoProdutoId ID do tipo de produto
     * @return Lista de DTOs com informações dos produtos do tipo especificado
     */
    @GetMapping("/produtos-por-tipo")
    @Operation(summary = "Gera relatório de produtos por tipo", 
               description = "Retorna uma lista de produtos do tipo especificado, com quantidade total de saídas e estoque atual")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operação bem-sucedida",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProdutoPorTipoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Tipo de produto não encontrado",
                    content = @Content)
    })
    public ResponseEntity<List<ProdutoPorTipoDTO>> gerarRelatorioProdutosPorTipo(
            @Parameter(description = "ID do tipo de produto", required = true)
            @RequestParam Long tipoProdutoId) {
        
        List<ProdutoPorTipoDTO> relatorio = relatorioService.gerarRelatorioProdutosPorTipo(tipoProdutoId);
        return ResponseEntity.ok(relatorio);
    }

    /**
     * Gera relatório de lucro por produto.
     *
     * @return Lista de DTOs com informações de lucro por produto
     */
    @GetMapping("/lucro-por-produto")
    @Operation(summary = "Gera relatório de lucro por produto", 
               description = "Retorna uma lista com ID/descrição do produto, total de unidades vendidas e lucro total")
    @ApiResponse(responseCode = "200", description = "Operação bem-sucedida",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LucroPorProdutoDTO.class)))
    public ResponseEntity<List<LucroPorProdutoDTO>> gerarRelatorioLucroPorProduto() {
        List<LucroPorProdutoDTO> relatorio = relatorioService.gerarRelatorioLucroPorProduto();
        return ResponseEntity.ok(relatorio);
    }
}