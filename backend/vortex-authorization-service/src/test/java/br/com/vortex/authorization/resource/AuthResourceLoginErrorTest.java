package br.com.vortex.authorization.resource;

import br.com.vortex.authorization.service.AuthService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static br.com.vortex.authorization.resource.LoginRateLimitTest.TOO_MANY;
import static br.com.vortex.authorization.resource.LoginRateLimitTest.login;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Como o POST /login responde a cada tipo de falha. Credencial errada e rate limit seguem 401 com a
 * mensagem de negócio; falha interna (pool esgotado, banco fora, chave do JWT ausente) não é credencial
 * inválida: responde 500 sem o texto da exceção. Antes tudo virava 401 com e.getMessage().
 */
@QuarkusTest
@TestHTTPEndpoint(AuthResource.class)
class AuthResourceLoginErrorTest {

    @InjectMock
    AuthService authService;

    @Test
    @DisplayName("Invalid credentials stay a 401 with the business message")
    void invalidCredentialsAreA401() {
        when(authService.login(any(), any(), any())).thenThrow(new NotAuthorizedException("Invalid credentials", "Bearer"));

        login("someone@test.com", "Whatever@123", null).statusCode(401)
            .body("success", is(false))
            .body("message", is("Invalid credentials"));
    }

    @Test
    @DisplayName("A rate-limited login stays a 401 with the business message")
    void rateLimitedLoginIsA401() {
        when(authService.login(any(), any(), any())).thenThrow(new BadRequestException(TOO_MANY));

        login("someone@test.com", "Whatever@123", null).statusCode(401).body("message", is(TOO_MANY));
    }

    @Test
    @DisplayName("An internal failure is a 500 that does not leak the exception text")
    void internalFailureIsA500WithoutDetails() {
        when(authService.login(any(), any(), any()))
            .thenThrow(new IllegalStateException("Unable to acquire JDBC Connection [pool exhausted]"));

        String body = login("someone@test.com", "Whatever@123", null).statusCode(500)
            .body("success", is(false))
            .extract().asString();
        assertFalse(body.contains("JDBC"), "o corpo não pode expor a exceção interna: " + body);
    }
}
