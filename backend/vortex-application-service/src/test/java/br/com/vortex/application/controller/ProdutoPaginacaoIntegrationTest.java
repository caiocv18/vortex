package br.com.vortex.application.controller;

import br.com.vortex.application.model.Produto;
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
 * O GET paginado de produtos pela porta HTTP, com a cadeia de segurança real (o token valida como VIEWER, o papel
 * mais fraco que lê): formato do {@code PagedModel}, padrões, teto do size, filtro {@code q} e ordenação.
 *
 * <p>Os produtos deste teste levam uma marca única na descrição e são gravados direto pelo repositório; o filtro
 * {@code q} pela marca isola essa massa do seed e do que as outras classes criam no mesmo banco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class ProdutoPaginacaoIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    private final String marca = "Paginacao-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> criados = new ArrayList<>();

    @Autowired
    private TestRestTemplate restTemplate;

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
    void apagaOsProdutosCriados() {
        produtoRepository.deleteAllById(criados);
    }

    @Test
    void semParametrosDevolveAPrimeiraPaginaDe20NoFormatoDoPagedModel() {
        Map<String, Object> body = get("/api/produtos");

        assertEquals(Set.of("content", "page"), body.keySet());
        Map<String, Object> page = page(body);
        assertEquals(Set.of("size", "number", "totalElements", "totalPages"), page.keySet());
        assertEquals(20, number(page, "size"));
        assertEquals(0, number(page, "number"));
        assertEquals(produtoRepository.count(), number(page, "totalElements"));
        assertEquals(Math.min(20, produtoRepository.count()), content(body).size());
        assertEquals(Set.of("id", "descricao", "valorFornecedor", "quantidadeEmEstoque", "tipoProdutoId"),
                content(body).getFirst().keySet());
    }

    @Test
    void ordenaPelaDescricaoEmOrdemCrescentePorPadrao() {
        criar(marca + " C");
        criar(marca + " A");
        criar(marca + " B");

        assertEquals(List.of(marca + " A", marca + " B", marca + " C"), descricoes(get("/api/produtos?q={q}", marca)));
    }

    @Test
    void ordenacaoPedidaVenceAPadrao() {
        criar(marca + " A");
        criar(marca + " B");

        assertEquals(List.of(marca + " B", marca + " A"),
                descricoes(get("/api/produtos?q={q}&sort={sort}", marca, "descricao,desc")));
    }

    @Test
    void filtraPelaDescricaoContidaSemDiferenciarMaiusculas() {
        criar(marca + " Teclado sem fio");
        criar(marca + " Mouse sem fio");

        Map<String, Object> body = get("/api/produtos?q={q}", marca.toUpperCase(Locale.ROOT) + " TECLADO");

        assertEquals(List.of(marca + " Teclado sem fio"), descricoes(body));
        assertEquals(1, number(page(body), "totalElements"));
    }

    @Test
    void filtroComAcentoAchaODoSeed() {
        // "Feijão 1kg" é o produto 6 do import.sql
        assertTrue(descricoes(get("/api/produtos?q={q}", "FEIJÃO")).contains("Feijão 1kg"));
    }

    @Test
    void qEmBrancoNaoFiltra() {
        long semFiltro = number(page(get("/api/produtos")), "totalElements");

        assertEquals(semFiltro, number(page(get("/api/produtos?q={q}", "   ")), "totalElements"));
        assertEquals(semFiltro, number(page(get("/api/produtos?q=")), "totalElements"));
    }

    @Test
    void curingaDoLikeNoQValeComoTexto() {
        criar(marca + " 100% algodão");
        criar(marca + " 1000 algodão");

        assertEquals(List.of(marca + " 100% algodão"), descricoes(get("/api/produtos?q={q}", marca + " 100%")));
    }

    @Test
    void sizeAcimaDe100EAtendidoCom100() {
        assertEquals(100, number(page(get("/api/produtos?size=500")), "size"));
    }

    @Test
    void paginaPedidaVemComONumeroEOsTotais() {
        criar(marca + " A");
        criar(marca + " B");
        criar(marca + " C");

        Map<String, Object> body = get("/api/produtos?q={q}&size=2&page=1", marca);

        assertEquals(List.of(marca + " C"), descricoes(body));
        Map<String, Object> page = page(body);
        assertEquals(2, number(page, "size"));
        assertEquals(1, number(page, "number"));
        assertEquals(3, number(page, "totalElements"));
        assertEquals(2, number(page, "totalPages"));
    }

    @Test
    void ordenacaoPorPropriedadeInexistenteE400() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/api/produtos?sort={sort}",
                HttpMethod.GET, new HttpEntity<>(viewer()), JSON_OBJECT, "naoExiste,asc");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_PARAMETER", response.getBody().get("errorCode"));
    }

    private void criar(String descricao) {
        Produto produto = new Produto();
        produto.setDescricao(descricao);
        produto.setValorFornecedor(new BigDecimal("10.00"));
        produto.setQuantidadeEmEstoque(1);
        produto.setTipoProduto(tipoProdutoRepository.findById(1L).orElseThrow());
        criados.add(produtoRepository.save(produto).getId());
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

    private static List<String> descricoes(Map<String, Object> body) {
        return content(body).stream().map(produto -> (String) produto.get("descricao")).toList();
    }

    private static long number(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).longValue();
    }
}
