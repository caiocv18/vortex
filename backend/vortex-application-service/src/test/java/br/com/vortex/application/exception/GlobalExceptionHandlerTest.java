package br.com.vortex.application.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Um erro inesperado volta como 500 com o ID do erro. Em produção ({@code prd} ou {@code cloud}) a mensagem é
 * genérica, sem nada da exceção; fora dela o detalhe ajuda a depurar.
 */
class GlobalExceptionHandlerTest {

    private static final String INTERNAL_DETAIL = "coluna senha_hash da tabela usuario";

    @ParameterizedTest
    @ValueSource(strings = {"cloud", "prd"})
    void productionHidesTheInternalMessage(String profile) {
        GlobalExceptionHandler.ErrorDetails body = handle(profile);

        assertNotNull(body.getErrorId());
        assertEquals("Ocorreu um erro interno no servidor. ID do erro: " + body.getErrorId(), body.getMessage());
        assertNull(body.getTechnicalDetails());
    }

    @Test
    void developmentShowsTheInternalMessage() {
        GlobalExceptionHandler.ErrorDetails body = handle("dev");

        assertNotNull(body.getErrorId());
        assertTrue(body.getMessage().contains(INTERNAL_DETAIL), body.getMessage());
        assertEquals("IllegalStateException: " + INTERNAL_DETAIL, body.getTechnicalDetails());
    }

    private static GlobalExceptionHandler.ErrorDetails handle(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);

        ResponseEntity<GlobalExceptionHandler.ErrorDetails> response = new GlobalExceptionHandler(environment)
                .handleGlobalException(new IllegalStateException(INTERNAL_DETAIL),
                        new ServletWebRequest(new MockHttpServletRequest("GET", "/api/produtos")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }
}
