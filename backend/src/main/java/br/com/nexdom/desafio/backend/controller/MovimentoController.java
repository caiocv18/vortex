package br.com.nexdom.desafio.backend.controller;

import br.com.nexdom.desafio.backend.dto.MovimentoEstoqueDTO;
import br.com.nexdom.desafio.backend.service.MovimentoEstoqueService;
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
 * Controller para operações relacionadas a MovimentoEstoque.
 */
@RestController
@RequestMapping("/api/movimentos")
@Tag(name = "Movimentos de Estoque", description = "API para gerenciamento de movimentos de estoque (entradas e saídas), com controle automático de estoque e cálculo de valores de venda")
public class MovimentoController {

    private final MovimentoEstoqueService movimentoEstoqueService;

    @Autowired
    public MovimentoController(MovimentoEstoqueService movimentoEstoqueService) {
        this.movimentoEstoqueService = movimentoEstoqueService;
    }

    /**
     * Cria um novo movimento de estoque.
     *
     * @param movimentoEstoqueDTO DTO com os dados do movimento de estoque
     * @return DTO do movimento de estoque criado
     */
    @PostMapping
    @Operation(summary = "Cria um novo movimento de estoque", description = "Cria um novo movimento de estoque com os dados fornecidos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Movimento de estoque criado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MovimentoEstoqueDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos ou estoque insuficiente",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado",
                    content = @Content)
    })
    public ResponseEntity<MovimentoEstoqueDTO> criar(
            @Parameter(description = "Dados do movimento de estoque a ser criado", required = true)
            @Valid @RequestBody MovimentoEstoqueDTO movimentoEstoqueDTO) {

        MovimentoEstoqueDTO createdMovimento = movimentoEstoqueService.criar(movimentoEstoqueDTO);
        return new ResponseEntity<>(createdMovimento, HttpStatus.CREATED);
    }

    /**
     * Busca todos os movimentos de estoque.
     *
     * @return Lista de DTOs dos movimentos de estoque
     */
    @GetMapping
    @Operation(summary = "Busca todos os movimentos de estoque", description = "Retorna uma lista com todos os movimentos de estoque cadastrados")
    @ApiResponse(responseCode = "200", description = "Operação bem-sucedida",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MovimentoEstoqueDTO.class)))
    public ResponseEntity<List<MovimentoEstoqueDTO>> buscarTodos() {
        List<MovimentoEstoqueDTO> movimentos = movimentoEstoqueService.buscarTodos();
        return ResponseEntity.ok(movimentos);
    }

    /**
     * Busca um movimento de estoque pelo ID.
     *
     * @param id ID do movimento de estoque
     * @return DTO do movimento de estoque
     */
    @GetMapping("/{id}")
    @Operation(summary = "Busca um movimento de estoque pelo ID", description = "Retorna um movimento de estoque específico pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operação bem-sucedida",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MovimentoEstoqueDTO.class))),
            @ApiResponse(responseCode = "404", description = "Movimento de estoque não encontrado",
                    content = @Content)
    })
    public ResponseEntity<MovimentoEstoqueDTO> buscarPorId(
            @Parameter(description = "ID do movimento de estoque a ser buscado", required = true)
            @PathVariable Long id) {

        MovimentoEstoqueDTO movimento = movimentoEstoqueService.buscarPorId(id);
        return ResponseEntity.ok(movimento);
    }

    /**
     * Atualiza um movimento de estoque.
     * Atenção: Esta operação é complexa e deve garantir a consistência do estoque.
     *
     * @param id ID do movimento de estoque
     * @param movimentoEstoqueDTO DTO com os novos dados do movimento de estoque
     * @return DTO do movimento de estoque atualizado
     */
    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um movimento de estoque", description = "Atualiza um movimento de estoque existente com os dados fornecidos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Movimento de estoque atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MovimentoEstoqueDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos ou estoque insuficiente",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Movimento de estoque ou produto não encontrado",
                    content = @Content)
    })
    public ResponseEntity<MovimentoEstoqueDTO> atualizar(
            @Parameter(description = "ID do movimento de estoque a ser atualizado", required = true)
            @PathVariable Long id,
            @Parameter(description = "Novos dados do movimento de estoque", required = true)
            @Valid @RequestBody MovimentoEstoqueDTO movimentoEstoqueDTO) {

        MovimentoEstoqueDTO updatedMovimento = movimentoEstoqueService.atualizar(id, movimentoEstoqueDTO);
        return ResponseEntity.ok(updatedMovimento);
    }

    /**
     * Exclui um movimento de estoque.
     * Atenção: Esta operação é complexa e deve garantir a consistência do estoque.
     *
     * @param id ID do movimento de estoque
     * @return Resposta sem conteúdo
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um movimento de estoque", description = "Exclui um movimento de estoque existente pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Movimento de estoque excluído com sucesso",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Movimento de estoque não encontrado",
                    content = @Content)
    })
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID do movimento de estoque a ser excluído", required = true)
            @PathVariable Long id) {

        movimentoEstoqueService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
