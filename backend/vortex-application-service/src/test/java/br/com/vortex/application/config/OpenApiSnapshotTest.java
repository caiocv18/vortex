package br.com.vortex.application.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A normalização do snapshot do OpenAPI: o que muda por execução ou por ordem de registro não pode mudar o texto,
 * e o resultado é estável quando normalizado de novo.
 */
class OpenApiSnapshotTest {

    /** Forma do /v3/api-docs: base path nos paths, porta aleatória, rotas fora do cliente do front. */
    private static final String REAL = """
            {"openapi":"3.1.0","info":{"title":"Vortex","version":"0.1.1"},
             "servers":[{"url":"http://localhost:54321","description":"Servidor de Desenvolvimento"}],
             "tags":[{"name":"Produtos"},{"name":"Filas"}],
             "paths":{
               "/api/queues":{"get":{"tags":["Filas"],"operationId":"filas","responses":{"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/QueueInfoDTO"}}}}}}},
               "/api/produtos":{"get":{"tags":["Produtos"],"operationId":"findAllProdutos","responses":{"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/PagedModelProdutoDTO"}}}}}}},
               "/api/produtosX":{"get":{"tags":["Produtos"],"operationId":"outro","responses":{}}},
               "/health":{"get":{"operationId":"health","responses":{}}}
             },
             "components":{"schemas":{
               "QueueInfoDTO":{"type":"object"},
               "PagedModelProdutoDTO":{"type":"object","properties":{"page":{"$ref":"#/components/schemas/PageMetadata"},"content":{"type":"array","items":{"$ref":"#/components/schemas/ProdutoDTO"}}}},
               "ProdutoDTO":{"type":"object","properties":{"descricao":{"type":"string","example":"Feijão 1kg"},"id":{"type":"integer"}}},
               "PageMetadata":{"type":"object","properties":{"size":{"type":"integer"}}}
             }}}
            """;

    @Test
    void baseParaNoServersPortaFixaESoAApiDeEstoque() {
        String normalizado = OpenApiSnapshot.normalizar(REAL);

        assertTrue(normalizado.contains("\"url\": \"http://localhost:8080/api\""), normalizado);
        assertTrue(normalizado.contains("\"/produtos\": {"), normalizado);
        assertFalse(normalizado.contains("/api/produtos"), normalizado);
        assertFalse(normalizado.contains("queues"), normalizado);
        assertFalse(normalizado.contains("produtosX"), normalizado);
        assertFalse(normalizado.contains("health"), normalizado);
    }

    @Test
    void schemasETagsQueSoAsRotasDeForaUsavamSaem() {
        String normalizado = OpenApiSnapshot.normalizar(REAL);

        assertFalse(normalizado.contains("QueueInfoDTO"), normalizado);
        assertFalse(normalizado.contains("Filas"), normalizado);
        // Alcançados por $ref encadeado: PagedModelProdutoDTO -> ProdutoDTO e PageMetadata
        assertTrue(normalizado.contains("\"ProdutoDTO\": {"), normalizado);
        assertTrue(normalizado.contains("\"PageMetadata\": {"), normalizado);
    }

    @Test
    void ordemDasChavesNaoMudaOTexto() {
        String invertido = """
                {"paths":{"/api/produtos":{"get":{"responses":{},"operationId":"x"}}},"openapi":"3.1.0"}
                """;
        String emOrdem = """
                {"openapi":"3.1.0","paths":{"/api/produtos":{"get":{"operationId":"x","responses":{}}}}}
                """;

        assertEquals(OpenApiSnapshot.normalizar(emOrdem), OpenApiSnapshot.normalizar(invertido));
    }

    @Test
    void normalizarDuasVezesDaOMesmoTexto() {
        String umaVez = OpenApiSnapshot.normalizar(REAL);

        assertEquals(umaVez, OpenApiSnapshot.normalizar(umaVez));
    }

    @Test
    void formatoDeSaida() {
        assertEquals("""
                {
                  "openapi": "3.1.0",
                  "paths": {
                    "/produtos": {
                      "get": {
                        "operationId": "x",
                        "responses": {},
                        "tags": []
                      }
                    }
                  }
                }
                """, OpenApiSnapshot.normalizar("""
                {"openapi":"3.1.0","paths":{"/api/produtos":{"get":{"operationId":"x","tags":[],"responses":{}}}}}
                """));
    }

    @Test
    void acentoSaiSemEscape() {
        assertTrue(OpenApiSnapshot.normalizar(REAL).contains("\"example\": \"Feijão 1kg\""));
    }

    @Test
    void divergenciaApontaAPrimeiraLinhaDiferente() {
        String mensagem = OpenApiContractTest.mensagemDeDivergencia("{\n  \"a\": 1\n}\n", "{\n  \"a\": 2\n}\n");

        assertTrue(mensagem.contains("a partir da linha 2"), mensagem);
        assertTrue(mensagem.contains("snapshot: \"a\": 1"), mensagem);
        assertTrue(mensagem.contains("real:     \"a\": 2"), mensagem);
        assertTrue(mensagem.contains("-Dopenapi.snapshot.update=true"), mensagem);
    }
}
