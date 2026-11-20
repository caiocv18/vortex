package br.com.vortex.application.config;

import br.com.vortex.application.service.AuthServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contrato da API com o front: o OpenAPI que o springdoc publica em {@code /v3/api-docs} tem de bater com o
 * snapshot {@code docs/application/openapi-backend.json}, de onde o {@code npm run generate-api} gera o cliente.
 * Os dois passam pela mesma normalização ({@link OpenApiSnapshot}) antes da comparação.
 *
 * <p>Divergiu: o real normalizado fica em {@code target/openapi-actual.json} (o CI sobe esse arquivo como artifact
 * {@code openapi-actual}) e o teste falha. Para regravar o snapshot quando a mudança de contrato é intencional:
 * {@code ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.snapshot.update=true}, revisar o diff do docs e
 * regenerar o cliente do front.
 *
 * <p>Perfil {@code test} (H2, sem broker externo). O {@code AuthServiceClient} simulado não é usado aqui, porque
 * {@code /v3/api-docs} é aberto fora da nuvem; ele está declarado para este teste reaproveitar o mesmo contexto
 * Spring das outras classes HTTP (mesma configuração, mesmo cache de contexto).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class OpenApiContractTest {

    static final String PROPRIEDADE_REGRAVAR = "openapi.snapshot.update";
    /** O surefire roda com o diretório do módulo como diretório de trabalho. */
    static final Path SNAPSHOT = Path.of("..", "..", "docs", "application", "openapi-backend.json");
    static final Path REAL = Path.of("target", "openapi-actual.json");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private AuthServiceClient authServiceClient;

    @Test
    void openApiRealBateComOSnapshotDoDocs() throws IOException {
        ResponseEntity<String> resposta = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        String real = OpenApiSnapshot.normalizar(resposta.getBody());

        if (Boolean.getBoolean(PROPRIEDADE_REGRAVAR)) {
            Files.writeString(SNAPSHOT, real, UTF_8);
            return;
        }

        String esperado = OpenApiSnapshot.normalizar(Files.readString(SNAPSHOT, UTF_8));
        if (!esperado.equals(real)) {
            Files.createDirectories(REAL.toAbsolutePath().getParent());
            Files.writeString(REAL, real, UTF_8);
            fail(mensagemDeDivergencia(esperado, real));
        }
    }

    static String mensagemDeDivergencia(String esperado, String real) {
        List<String> linhasEsperadas = esperado.lines().toList();
        List<String> linhasReais = real.lines().toList();
        int linha = 0;
        while (linha < linhasEsperadas.size() && linha < linhasReais.size()
                && linhasEsperadas.get(linha).equals(linhasReais.get(linha))) {
            linha++;
        }
        return "O OpenAPI real (/v3/api-docs) divergiu de docs/application/openapi-backend.json a partir da linha "
                + (linha + 1) + " do JSON normalizado:\n"
                + "  snapshot: " + (linha < linhasEsperadas.size() ? linhasEsperadas.get(linha).strip() : "(fim)") + "\n"
                + "  real:     " + (linha < linhasReais.size() ? linhasReais.get(linha).strip() : "(fim)") + "\n"
                + "O real normalizado está em backend/vortex-application-service/" + REAL + " (no CI, no artifact "
                + "openapi-actual). Se a mudança de contrato é intencional, regrave o snapshot com "
                + "./mvnw test -Dtest=OpenApiContractTest -D" + PROPRIEDADE_REGRAVAR + "=true (ou copie esse "
                + "arquivo para docs/application/openapi-backend.json), revise o diff e rode npm run generate-api no front.";
    }
}
