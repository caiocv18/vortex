package br.com.vortex.application.controller;

import br.com.vortex.application.dto.TipoProdutoDTO;
import br.com.vortex.application.service.TipoProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para operações relacionadas a TipoProduto.
 */
@RestController
@RequestMapping("/api/tipos-produto")
@Tag(name = "Tipos de Produto", description = "API para gerenciamento de tipos de produto")
public class TipoProdutoController {

    private final TipoProdutoService tipoProdutoService;

    @Autowired
    public TipoProdutoController(TipoProdutoService tipoProdutoService) {
        this.tipoProdutoService = tipoProdutoService;
    }

    /**
     * Cria um novo tipo de produto.
     *
     * @param tipoProdutoDTO DTO com os dados do tipo de produto
     * @return DTO do tipo de produto criado
     */
    @PostMapping
    @Operation(summary = "Cria um novo tipo de produto", description = "Cria um novo tipo de produto com os dados fornecidos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tipo de produto criado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TipoProdutoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content)
    })
    public ResponseEntity<TipoProdutoDTO> criar(
            @Parameter(description = "Dados do tipo de produto a ser criado", required = true)
            @Valid @RequestBody TipoProdutoDTO tipoProdutoDTO) {
        
        TipoProdutoDTO createdTipoProduto = tipoProdutoService.criar(tipoProdutoDTO);
        return new ResponseEntity<>(createdTipoProduto, HttpStatus.CREATED);
    }

    /**
     * Busca todos os tipos de produto.
     *
     * @return Lista de DTOs dos tipos de produto
     */
    @GetMapping
    @Operation(summary = "Busca todos os tipos de produto", description = "Retorna uma lista com todos os tipos de produto cadastrados")
    @ApiResponse(responseCode = "200", description = "Operação bem-sucedida",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TipoProdutoDTO.class)))
    public ResponseEntity<List<TipoProdutoDTO>> buscarTodos() {
        List<TipoProdutoDTO> tiposProduto = tipoProdutoService.buscarTodos();
        return ResponseEntity.ok(tiposProduto);
    }

    /**
     * Busca um tipo de produto pelo ID.
     *
     * @param id ID do tipo de produto
     * @return DTO do tipo de produto
     */
    @GetMapping("/{id}")
    @Operation(summary = "Busca um tipo de produto pelo ID", description = "Retorna um tipo de produto específico pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operação bem-sucedida",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TipoProdutoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Tipo de produto não encontrado",
                    content = @Content)
    })
    public ResponseEntity<TipoProdutoDTO> buscarPorId(
            @Parameter(description = "ID do tipo de produto a ser buscado", required = true)
            @PathVariable Long id) {
        
        TipoProdutoDTO tipoProduto = tipoProdutoService.buscarPorId(id);
        return ResponseEntity.ok(tipoProduto);
    }

    /**
     * Atualiza um tipo de produto.
     *
     * @param id ID do tipo de produto
     * @param tipoProdutoDTO DTO com os novos dados do tipo de produto
     * @return DTO do tipo de produto atualizado
     */
    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um tipo de produto", description = "Atualiza um tipo de produto existente com os dados fornecidos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tipo de produto atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TipoProdutoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Tipo de produto não encontrado",
                    content = @Content)
    })
    public ResponseEntity<TipoProdutoDTO> atualizar(
            @Parameter(description = "ID do tipo de produto a ser atualizado", required = true)
            @PathVariable Long id,
            @Parameter(description = "Novos dados do tipo de produto", required = true)
            @Valid @RequestBody TipoProdutoDTO tipoProdutoDTO) {
        
        TipoProdutoDTO updatedTipoProduto = tipoProdutoService.atualizar(id, tipoProdutoDTO);
        return ResponseEntity.ok(updatedTipoProduto);
    }

    /**
     * Exclui um tipo de produto.
     *
     * @param id ID do tipo de produto
     * @return Resposta sem conteúdo
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um tipo de produto", description = "Exclui um tipo de produto existente pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tipo de produto excluído com sucesso",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Tipo de produto não encontrado",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Não é possível excluir um tipo de produto que possui produtos associados",
                    content = @Content)
    })
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID do tipo de produto a ser excluído", required = true)
            @PathVariable Long id) {
        
        tipoProdutoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}