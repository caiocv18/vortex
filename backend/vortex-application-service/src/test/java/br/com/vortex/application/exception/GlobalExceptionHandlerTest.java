package br.com.vortex.application.exception;

import br.com.vortex.application.config.BusinessTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @Test
    void insufficientStockIsBadRequestStampedInTheBusinessZone() {
        LocalDateTime before = LocalDateTime.now(BusinessTime.ZONE);

        ResponseEntity<GlobalExceptionHandler.ErrorDetails> response = new GlobalExceptionHandler(new MockEnvironment())
                .handleEstoqueInsuficienteException(new EstoqueInsuficienteException("sem estoque"), request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ESTOQUE_INSUFICIENTE", response.getBody().getErrorCode());
        assertStampedSince(before, response.getBody().getTimestamp());
    }

    @Test
    void invalidArgumentListsTheFieldErrors() throws NoSuchMethodException {
        LocalDateTime before = LocalDateTime.now(BusinessTime.ZONE);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Form(), "form");
        binding.rejectValue("nome", "NotBlank", "nome é obrigatório");
        MethodParameter parameter = new MethodParameter(Form.class.getDeclaredMethod("setNome", String.class), 0);

        ResponseEntity<Object> response = new GlobalExceptionHandler(new MockEnvironment())
                .handleMethodArgumentNotValidException(new MethodArgumentNotValidException(parameter, binding), request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        GlobalExceptionHandler.ValidationErrorDetails body =
                assertInstanceOf(GlobalExceptionHandler.ValidationErrorDetails.class, response.getBody());
        assertEquals(Map.of("nome", "nome é obrigatório"), body.getErrors());
        assertStampedSince(before, body.getTimestamp());
    }

    /** Fora do fuso de negócio (o runner roda em UTC) o carimbo cairia 3 h à frente e reprovaria aqui. */
    private static void assertStampedSince(LocalDateTime before, LocalDateTime stamp) {
        assertFalse(stamp.isBefore(before), stamp + " antes de " + before);
        assertFalse(stamp.isAfter(LocalDateTime.now(BusinessTime.ZONE)), stamp + " no futuro");
    }

    private static ServletWebRequest request() {
        return new ServletWebRequest(new MockHttpServletRequest("POST", "/api/movimentos"));
    }

    /** Alvo do binding: o BeanPropertyBindingResult lê a propriedade pelo getter. */
    static class Form {
        private String nome;

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }
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
