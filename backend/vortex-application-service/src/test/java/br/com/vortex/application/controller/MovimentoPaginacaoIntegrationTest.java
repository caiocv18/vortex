package br.com.vortex.application.controller;

import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import br.com.vortex.application.repository.TipoProdutoRepository;
import br.com.vortex.application.service.AuthServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * O GET paginado de movimentos pela porta HTTP, com a cadeia de segurança real (o token valida como VIEWER):
 * formato do {@code PagedModel}, padrões (20 por página, mais recente primeiro), teto do size, filtros {@code q}
 * (descrição do produto) e {@code tipo}, e o 400 de um tipo que não existe.
 *
 * <p>Produto e movimentos deste teste são gravados direto pelos repositórios, com datas fixas para provar a
 * ordenação, e levam uma marca única na descrição do produto; o filtro {@code q} pela marca isola essa massa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class MovimentoPaginacaoIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    private final String marca = "Paginacao-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> movimentosCriados = new ArrayList<>();
    private final List<Long> produtosCriados = new ArrayList<>();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MovimentoEstoqueRepository movimentoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private TipoProdutoRepository tipoProdutoRepository;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @BeforeEach
    void tokenValidatesAsViewer() {
        when(authServiceClient.validateToken(anyString())).thenReturn(CompletableFuture.completedFuture(
                Map.of("valid", true, "username", "paginacao", "userId", "1", "roles", List.of("VIEWER"))));
    }

    @AfterEach
    void apagaOQueFoiCriado() {
        movimentoRepository.deleteAllById(movimentosCriados);
        produtoRepository.deleteAllById(produtosCriados);
    }

    @Test
    void semParametrosDevolveAPrimeiraPaginaDe20NoFormatoDoPagedModel() {
        Map<String, Object> body = get("/api/movimentos");

        assertEquals(Set.of("content", "page"), body.keySet());
        Map<String, Object> page = page(body);
        assertEquals(Set.of("size", "number", "totalElements", "totalPages"), page.keySet());
        assertEquals(20, number(page, "size"));
        assertEquals(0, number(page, "number"));
        assertEquals(movimentoRepository.count(), number(page, "totalElements"));
        assertEquals(Math.min(20, movimentoRepository.count()), content(body).size());
    }

    @Test
    void ordenaPeloMaisRecentePorPadrao() {
        Produto produto = produto(marca + " Caderno");
        long primeiro = movimento(produto, TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 1, 9, 0));
        long terceiro = movimento(produto, TipoMovimentacao.SAIDA, LocalDateTime.of(2024, 1, 3, 9, 0));
        long segundo = movimento(produto, TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 2, 9, 0));

        assertEquals(List.of(terceiro, segundo, primeiro), ids(get("/api/movimentos?q={q}", marca)));
    }

    @Test
    void ordenacaoPedidaVenceAPadrao() {
        Produto produto = produto(marca + " Caderno");
        long antigo = movimento(produto, TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 1, 9, 0));
        long recente = movimento(produto, TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 2, 9, 0));

        assertEquals(List.of(antigo, recente),
                ids(get("/api/movimentos?q={q}&sort={sort}", marca, "dataMovimento,asc")));
    }

    @Test
    void filtraPelaDescricaoDoProdutoSemDiferenciarMaiusculas() {
        long doCaderno = movimento(produto(marca + " Caderno"), TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 1, 9, 0));
        movimento(produto(marca + " Lápis"), TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 2, 9, 0));

        Map<String, Object> body = get("/api/movimentos?q={q}", marca.toLowerCase(Locale.ROOT) + " CADERNO");

        assertEquals(List.of(doCaderno), ids(body));
        assertEquals(1, number(page(body), "totalElements"));
    }

    @Test
    void filtraPeloTipoECombinaComOQ() {
        Produto produto = produto(marca + " Caderno");
        movimento(produto, TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 1, 9, 0));
        long saida = movimento(produto, TipoMovimentacao.SAIDA, LocalDateTime.of(2024, 1, 2, 9, 0));

        assertEquals(List.of(saida), ids(get("/api/movimentos?q={q}&tipo=SAIDA", marca)));
        assertEquals(2, ids(get("/api/movimentos?q={q}&tipo=", marca)).size(), "tipo vazio não filtra");
    }

    @Test
    void soOTipoFiltraSemQ() {
        List<Map<String, Object>> saidas = content(get("/api/movimentos?tipo=SAIDA&size=100"));

        assertTrue(saidas.stream().allMatch(movimento -> "SAIDA".equals(movimento.get("tipoMovimentacao"))), saidas.toString());
    }

    @Test
    void curingaDoLikeNoQValeComoTexto() {
        long comUnderline = movimento(produto(marca + " fio_2mm"), TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 1, 9, 0));
        movimento(produto(marca + " fioX2mm"), TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 2, 9, 0));

        assertEquals(List.of(comUnderline), ids(get("/api/movimentos?q={q}", marca + " fio_2")));
    }

    @Test
    void cadaMovimentoDaListaTrazADescricaoDoProduto() {
        movimento(produto(marca + " Régua 30cm"), TipoMovimentacao.ENTRADA, LocalDateTime.of(2024, 1, 1, 9, 0));

        Map<String, Object> movimento = content(get("/api/movimentos?q={q}", marca)).getFirst();

        assertEquals(marca + " Régua 30cm", movimento.get("produtoDescricao"));
        assertEquals(Set.of("id", "dataMovimento", "tipoMovimentacao", "quantidadeMovimentada", "valorVenda",
                "produtoId", "produtoDescricao"), movimento.keySet());
    }

    @Test
    void sizeAcimaDe100EAtendidoCom100() {
        assertEquals(100, number(page(get("/api/movimentos?size=500")), "size"));
    }

    @Test
    void tipoInexistenteE400ComOsValoresAceitos() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/api/movimentos?tipo={tipo}",
                HttpMethod.GET, new HttpEntity<>(viewer()), JSON_OBJECT, "TROCA");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_PARAMETER", response.getBody().get("errorCode"));
        assertEquals("Valor inválido para o parâmetro 'tipo'. Valores aceitos: ENTRADA, SAIDA",
                response.getBody().get("message"));
    }

    private Produto produto(String descricao) {
        Produto produto = new Produto();
        produto.setDescricao(descricao);
        produto.setValorFornecedor(new BigDecimal("10.00"));
        produto.setQuantidadeEmEstoque(100);
        produto.setTipoProduto(tipoProdutoRepository.findById(1L).orElseThrow());
        Produto salvo = produtoRepository.save(produto);
        produtosCriados.add(salvo.getId());
        return salvo;
    }

    private long movimento(Produto produto, TipoMovimentacao tipo, LocalDateTime quando) {
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produto);
        movimento.setTipoMovimentacao(tipo);
        movimento.setQuantidadeMovimentada(1);
        movimento.setDataMovimento(quando);
        movimento.setValorVenda(tipo == TipoMovimentacao.SAIDA ? new BigDecimal("13.50") : null);
        long id = movimentoRepository.save(movimento).getId();
        movimentosCriados.add(id);
        return id;
    }

    private Map<String, Object> get(String url, Object... uriVariables) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(viewer()), JSON_OBJECT, uriVariables);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "GET " + url + ": " + response.getBody());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private static HttpHeaders viewer() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("VIEWER");
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> page(Map<String, Object> body) {
        return (Map<String, Object>) body.get("page");
    }

    private static List<Long> ids(Map<String, Object> body) {
        return content(body).stream().map(movimento -> ((Number) movimento.get("id")).longValue()).toList();
    }

    private static long number(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).longValue();
    }
}
