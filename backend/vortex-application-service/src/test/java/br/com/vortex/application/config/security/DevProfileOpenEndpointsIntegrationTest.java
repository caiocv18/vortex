package br.com.vortex.application.config.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O outro lado do {@link CloudProfileSecurityIntegrationTest}: no perfil {@code dev} Swagger, {@code /v3/api-docs},
 * console do H2 e {@code /api/test} continuam abertos sem token, porque o fechamento é só da nuvem, e o CORS segue
 * aceitando o frontend local. O H2 ganha um
 * nome próprio, para o import.sql não cair num banco que outro contexto da mesma JVM já semeou.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:dev-open-endpoints-${random.uuid};DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevProfileOpenEndpointsIntegrationTest {

    private static final String LOCAL_FRONTEND = "http://localhost:5173";

    @Autowired
    private TestRestTemplate restTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/v3/api-docs", "/h2-console/", "/api/test/public"})
    void devKeepsDocsConsoleAndTestEndpointsOpen(String path) {
        assertEquals(HttpStatus.OK, restTemplate.getForEntity(path, String.class).getStatusCode(), path);
    }

    /** O health do Actuator responde sem token; os detalhes (componentes) ficam fechados. */
    @Test
    void devExposesTheActuatorHealthWithoutDetails() {
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);

        assertNotEquals(HttpStatus.NOT_FOUND, health.getStatusCode());
        assertTrue(health.getBody().contains("\"status\""), health.getBody());
        assertFalse(health.getBody().contains("\"components\""), health.getBody());
    }

    /**
     * O SecurityConfig local termina em {@code anyRequest().permitAll()}: qualquer endpoint do Actuator exposto
     * estaria aberto sem login. Só o health é exposto, então os outros nem existem. (Caminho sem handler sai hoje
     * como 500, e não 404, porque o {@code GlobalExceptionHandler} trata o {@code NoResourceFoundException} como
     * erro genérico; o que importa aqui é que nenhum responde 2xx.)
     */
    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/metrics", "/actuator/info", "/actuator/beans",
            "/actuator/configprops", "/actuator/loggers", "/actuator/heapdump", "/actuator/threaddump"})
    void devExposesNoOtherActuatorEndpoint(String path) {
        assertFalse(restTemplate.getForEntity(path, String.class).getStatusCode().is2xxSuccessful(), path);
    }

    /** A lista de origens do cloud não vaza para o dev: o frontend local continua passando no preflight. */
    @Test
    void devKeepsTheLocalFrontendOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(LOCAL_FRONTEND);
        headers.setAccessControlRequestMethod(HttpMethod.POST);

        ResponseEntity<String> preflight = restTemplate.exchange("/api/tipos-produto", HttpMethod.OPTIONS,
                new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, preflight.getStatusCode());
        assertEquals(LOCAL_FRONTEND, preflight.getHeaders().getAccessControlAllowOrigin());
    }
}
