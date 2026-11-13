package br.com.vortex.application.config;

import br.com.vortex.application.service.AuthServiceClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * O perfil {@code postgres} sozinho (sem o {@code test}, que traria o H2) contra um PostgreSQL 18 real: o Flyway
 * cria o esquema (V1) e semeia (V2), o Hibernate valida, e a API grava e lê pela porta HTTP com a cadeia de
 * segurança real. Só o validate-token do serviço de autorização é simulado (o token é o nome do papel).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresProfileIntegrationTest extends PostgresContainerSupport {

    private static final String SEED = "db/migration/postgres/V2__seed_demo_data.sql";
    private static final List<String> SEQUENCES = List.of("tipo_produto_seq", "produto_seq", "movimento_estoque_seq");
    /** Contagem total, contagem das linhas do seed e posição da sequence contra o MAX(id), por tabela. */
    private static final List<TableQueries> TABLES = List.of(
            new TableQueries("tipo_produto", 10,
                    "SELECT COUNT(*) FROM tipo_produto",
                    "SELECT COUNT(*) FROM tipo_produto WHERE id BETWEEN 1 AND 10",
                    "SELECT (SELECT last_value FROM tipo_produto_seq) >= (SELECT MAX(id) FROM tipo_produto)"),
            new TableQueries("produto", 20,
                    "SELECT COUNT(*) FROM produto",
                    "SELECT COUNT(*) FROM produto WHERE id BETWEEN 1 AND 20",
                    "SELECT (SELECT last_value FROM produto_seq) >= (SELECT MAX(id) FROM produto)"),
            new TableQueries("movimento_estoque", 20,
                    "SELECT COUNT(*) FROM movimento_estoque",
                    "SELECT COUNT(*) FROM movimento_estoque WHERE id BETWEEN 1 AND 20",
                    "SELECT (SELECT last_value FROM movimento_estoque_seq) >= (SELECT MAX(id) FROM movimento_estoque)"));
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @BeforeEach
    void eachTokenValidatesAsTheRoleItNames() {
        when(authServiceClient.validateToken(anyString())).thenAnswer(invocation -> CompletableFuture.completedFuture(
                Map.of("valid", true, "username", "postgres-test", "userId", "1",
                        "roles", List.of(invocation.getArgument(0, String.class)))));
    }

    @Test
    void applicationRunsOnPostgresWithTheFlywaySchema() {
        String product = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        Integer major = jdbc.execute((ConnectionCallback<Integer>) c -> c.getMetaData().getDatabaseMajorVersion());

        assertEquals("PostgreSQL", product);
        assertEquals(18, major);
        assertEquals(List.of("1", "2"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class));
    }

    @Test
    void seedIsPresentWithItsAccentsAndTheSequencesAhead() {
        for (TableQueries table : TABLES) {
            assertEquals(table.seededRows(), jdbc.queryForObject(table.countSeeded(), Integer.class), table.name());
            assertEquals(Boolean.TRUE, jdbc.queryForObject(table.sequenceAhead(), Boolean.class),
                    table.name() + "_seq atrás do MAX(id): o próximo insert colidiria com o seed");
        }
        assertEquals("Eletrônicos", jdbc.queryForObject("SELECT nome FROM tipo_produto WHERE id = 1", String.class));
    }

    @Test
    void seedCanRunAgainWithoutDuplicatingOrRewinding() {
        // Um id consumido sem linha deixa cada sequence à frente do MAX(id): um setval para trás apareceria aqui
        SEQUENCES.forEach(sequence -> jdbc.queryForObject("SELECT nextval(?::regclass)", Long.class, sequence));
        List<Integer> rowsBefore = countAllRows();
        List<Long> sequencesBefore = lastValues();

        runSeedScript();

        assertEquals(rowsBefore, countAllRows());
        assertEquals(sequencesBefore, lastValues(), "o seed rebobinou uma sequence");
    }

    /**
     * Todo boot chama o migrate do Flyway. Como o seed é a V2, aplicada uma vez só, um boot depois de a demo apagar
     * uma linha do seed não a repõe (um movimento reposto voltaria sem o efeito no estoque que o DELETE desfez) e
     * não rebobina sequence nenhuma (um setval para trás reemitiria ids já usados).
     */
    @Test
    void aRestartKeepsWhatTheDemoDeletedAndNeverReusesAnId() {
        // O produto 20 do seed não tem movimento, então quem escreve pode apagá-lo
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE, "/api/produtos/20", null).getStatusCode());
        long deleted = Long.parseLong(create("/api/produtos", produto("Produto apagado antes do boot")));
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE, "/api/produtos/" + deleted, null).getStatusCode());
        try {
            flyway.migrate();

            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM produto WHERE id = 20", Integer.class),
                    "o boot repôs uma linha do seed que a demo apagou");
            String next = create("/api/produtos", produto("Produto criado depois do boot"));
            assertTrue(Long.parseLong(next) > deleted, "o boot rebobinou a sequence e reemitiu um id já usado");
            assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE, "/api/produtos/" + next, null).getStatusCode());
        } finally {
            // Devolve o produto 20 às outras classes; o script pula todo o resto, que já existe
            runSeedScript();
        }
    }

    @Test
    void writerManagesTipoProdutoWithAnIdAfterTheSeed() {
        long seededMax = jdbc.queryForObject("SELECT MAX(id) FROM tipo_produto", Long.class);

        String path = "/api/tipos-produto/" + create("/api/tipos-produto", Map.of("nome", "Tipo criado no PostgreSQL"));

        assertTrue(Long.parseLong(path.substring(path.lastIndexOf('/') + 1)) > seededMax, "o id novo colidiria com o seed");
        assertEquals(HttpStatus.OK, send(HttpMethod.GET, path, null).getStatusCode());
        assertEquals(HttpStatus.OK, send(HttpMethod.PUT, path, Map.of("nome", "Tipo alterado no PostgreSQL")).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE, path, null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, send(HttpMethod.GET, path, null).getStatusCode());
    }

    @Test
    void movementsUpdateStockAndReportsReadThemOnPostgres() {
        String produtoId = create("/api/produtos", produto("Produto do teste no PostgreSQL"));
        String entrada = create("/api/movimentos",
                Map.of("tipoMovimentacao", "ENTRADA", "quantidadeMovimentada", 3, "produtoId", Long.parseLong(produtoId)));
        String saida = create("/api/movimentos",
                Map.of("tipoMovimentacao", "SAIDA", "quantidadeMovimentada", 2, "produtoId", Long.parseLong(produtoId)));

        ResponseEntity<Map<String, Object>> produto = restTemplate.exchange("/api/produtos/" + produtoId,
                HttpMethod.GET, new HttpEntity<>(bearer("USER")), JSON_OBJECT);
        assertEquals(HttpStatus.OK, produto.getStatusCode());
        assertNotNull(produto.getBody());
        assertEquals(6, ((Number) produto.getBody().get("quantidadeEmEstoque")).intValue());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimento_estoque WHERE id = ? AND valor_venda > 0", Integer.class, Long.parseLong(saida)));
        assertEquals(HttpStatus.OK, send(HttpMethod.GET, "/api/relatorios/lucro-por-produto", null).getStatusCode());

        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE, "/api/movimentos/" + saida, null).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE, "/api/movimentos/" + entrada, null).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE, "/api/produtos/" + produtoId, null).getStatusCode());
    }

    /** Produto com 5 unidades em estoque, do tipo 1 do seed. */
    private static Map<String, Object> produto(String descricao) {
        return Map.of("descricao", descricao, "valorFornecedor", 10.5, "quantidadeEmEstoque", 5, "tipoProdutoId", 1);
    }

    private List<Integer> countAllRows() {
        return TABLES.stream().map(table -> jdbc.queryForObject(table.countAll(), Integer.class)).toList();
    }

    private List<Long> lastValues() {
        return SEQUENCES.stream()
                .map(sequence -> jdbc.queryForObject(
                        "SELECT last_value FROM pg_sequences WHERE schemaname = 'public' AND sequencename = ?",
                        Long.class, sequence))
                .toList();
    }

    private void runSeedScript() {
        ResourceDatabasePopulator seed = new ResourceDatabasePopulator(new ClassPathResource(SEED));
        seed.setSqlScriptEncoding("UTF-8");
        seed.execute(dataSource);
    }

    /** POST como USER que precisa dar 201; devolve o id criado. */
    private String create(String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(body, bearer("USER")), JSON_OBJECT);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), "POST " + path + ": " + created.getBody());
        assertNotNull(created.getBody());
        return String.valueOf(created.getBody().get("id"));
    }

    private ResponseEntity<String> send(HttpMethod method, String path, Object body) {
        return restTemplate.exchange(path, method, new HttpEntity<>(body, bearer("USER")), String.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private record TableQueries(String name, int seededRows, String countAll, String countSeeded, String sequenceAhead) {
    }
}
