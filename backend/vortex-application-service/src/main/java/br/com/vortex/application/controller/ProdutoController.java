package br.com.vortex.application.controller;

import br.com.vortex.application.config.security.CanRead;
import br.com.vortex.application.config.security.CanWrite;
import br.com.vortex.application.dto.ProdutoDTO;
import br.com.vortex.application.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para operações relacionadas a Produto.
 */
@RestController
@RequestMapping("/api/produtos")
@Tag(name = "Produtos", description = "API para gerenciamento de produtos")
@CanWrite
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    /**
     * Cria um novo produto.
     *
     * @param produtoDTO DTO com os dados do produto
     * @return DTO do produto criado
     */
    @PostMapping
    @Operation(operationId = "createProduto", summary = "Cria um novo produto", description = "Cria um novo produto com os dados fornecidos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Produto criado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProdutoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Tipo de produto não encontrado",
                    content = @Content)
    })
    public ResponseEntity<ProdutoDTO> criar(
            @Parameter(description = "Dados do produto a ser criado", required = true)
            @Valid @RequestBody ProdutoDTO produtoDTO) {
        
        ProdutoDTO createdProduto = produtoService.criar(produtoDTO);
        return new ResponseEntity<>(createdProduto, HttpStatus.CREATED);
    }

    /**
     * Busca uma página de produtos, com filtro opcional pela descrição.
     *
     * @param q trecho da descrição; nulo ou em branco não filtra
     * @param pageable página (a partir de 0), tamanho (padrão 20, teto 100) e ordenação (padrão descricao,asc)
     * @return Página de DTOs dos produtos, no formato do {@link PagedModel}
     */
    @CanRead
    @GetMapping
    @Operation(operationId = "findAllProdutos", summary = "Busca os produtos, uma página por vez",
            description = "Retorna uma página de produtos no formato {\"content\": [...], \"page\": {\"size\", \"number\", "
                    + "\"totalElements\", \"totalPages\"}}. Sem parâmetros: page=0, size=20, sort=descricao,asc. "
                    + "Um size acima de 100 é atendido com 100. O q filtra pela descrição (contém, sem diferenciar "
                    + "maiúsculas); vazio ou ausente não filtra.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operação bem-sucedida"),
            @ApiResponse(responseCode = "400", description = "Ordenação por propriedade inexistente",
                    content = @Content)
    })
    public ResponseEntity<PagedModel<ProdutoDTO>> buscar(
            @Parameter(description = "Trecho da descrição do produto (contém, sem diferenciar maiúsculas)",
                    example = "notebook")
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "descricao") Pageable pageable) {

        return ResponseEntity.ok(new PagedModel<>(produtoService.buscar(q, pageable)));
    }

    /**
     * Busca um produto pelo ID.
     *
     * @param id ID do produto
     * @return DTO do produto
     */
    @CanRead
    @GetMapping("/{id}")
    @Operation(operationId = "findProdutoById", summary = "Busca um produto pelo ID", description = "Retorna um produto específico pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Operação bem-sucedida",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProdutoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado",
                    content = @Content)
    })
    public ResponseEntity<ProdutoDTO> buscarPorId(
            @Parameter(description = "ID do produto a ser buscado", required = true)
            @PathVariable Long id) {
        
        ProdutoDTO produto = produtoService.buscarPorId(id);
        return ResponseEntity.ok(produto);
    }

    /**
     * Atualiza um produto.
     *
     * @param id ID do produto
     * @param produtoDTO DTO com os novos dados do produto
     * @return DTO do produto atualizado
     */
    @PutMapping("/{id}")
    @Operation(operationId = "updateProduto", summary = "Atualiza um produto", description = "Atualiza um produto existente com os dados fornecidos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto atualizado com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProdutoDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos fornecidos",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Produto ou tipo de produto não encontrado",
                    content = @Content)
    })
    public ResponseEntity<ProdutoDTO> atualizar(
            @Parameter(description = "ID do produto a ser atualizado", required = true)
            @PathVariable Long id,
            @Parameter(description = "Novos dados do produto", required = true)
            @Valid @RequestBody ProdutoDTO produtoDTO) {
        
        ProdutoDTO updatedProduto = produtoService.atualizar(id, produtoDTO);
        return ResponseEntity.ok(updatedProduto);
    }

    /**
     * Exclui um produto.
     *
     * @param id ID do produto
     * @return Resposta sem conteúdo
     */
    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteProduto", summary = "Exclui um produto", description = "Exclui um produto existente pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Produto excluído com sucesso",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Não é possível excluir um produto que possui movimentos de estoque associados",
                    content = @Content)
    })
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID do produto a ser excluído", required = true)
            @PathVariable Long id) {
        
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}