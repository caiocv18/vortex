package br.com.vortex.authorization.resource;

import br.com.vortex.authorization.dto.RegisterRequest;
import br.com.vortex.authorization.entity.LoginAttempt;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static br.com.vortex.authorization.resource.LoginRateLimitTest.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cliente fora da lista de proxies confiáveis mandando X-Forwarded-For forjado: o cabeçalho é ignorado e
 * o rate limit conta contra o endereço real da conexão. Antes da correção o AuthResource lia o cabeçalho
 * direto e bastava trocá-lo a cada tentativa.
 *
 * Roda na configuração entregue, sem TRUSTED_PROXIES: nenhum proxy é confiável, nem o loopback. Nos
 * modos do projeto o navegador fala direto com a porta 8081 (por loopback no leve e no dev, pelo gateway
 * da bridge no compose), e confiar nesses endereços, como o padrão anterior fazia, deixava qualquer
 * cliente escolher o próprio IP. O cliente de teste conecta por loopback, como o navegador. Com
 * TRUSTED_PROXIES declarando o loopback o cabeçalho passa a valer: ver o LoginRateLimitTest.
 */
@QuarkusTest
@TestHTTPEndpoint(AuthResource.class)
class UntrustedProxyLoginTest {

    // Faixa de documentação (RFC 5737), usada como IP forjado
    private static final String SPOOFED_PREFIX = "203.0.113.";

    @Inject
    @ConfigProperty(name = "auth.rate-limit.login-attempts")
    int maxAttempts;

    @BeforeEach
    void clearAttemptsBefore() {
        clearAttempts();
    }

    @AfterEach
    void clearAttemptsAfter() {
        clearAttempts();
    }

    @Test
    @DisplayName("By default a spoofed X-Forwarded-For, even from loopback, is ignored: the real address is recorded")
    void spoofedForwardedForIsIgnored() throws Exception {
        RegisterRequest user = registerUser();

        login(user.email, WRONG_PASSWORD, SPOOFED_PREFIX + "7").statusCode(401).body("message", is("Invalid credentials"));

        List<LoginAttempt> attempts = recordedAttempts(user.email);
        assertEquals(1, attempts.size());
        String recorded = attempts.get(0).ipAddress;
        assertNotEquals(SPOOFED_PREFIX + "7", recorded);
        assertTrue(InetAddress.getByName(recorded).isLoopbackAddress(),
            "esperado o endereço real da conexão (loopback), veio " + recorded);
    }

    @Test
    @DisplayName("Rotating a spoofed X-Forwarded-For does not escape the limit: failures count against the real IP")
    void rotatingSpoofedForwardedForDoesNotEscapeTheLimit() {
        RegisterRequest victim = registerUser();
        RegisterRequest other = registerUser();

        // Um IP forjado diferente por tentativa, como faria quem tenta driblar o limite por IP
        for (int i = 1; i <= maxAttempts; i++) {
            login(victim.email, WRONG_PASSWORD, SPOOFED_PREFIX + i).statusCode(401).body("message", is("Invalid credentials"));
        }

        // A outra conta nunca errou a senha: o bloqueio só pode vir do IP real (loopback),
        // que acumulou as falhas apesar dos cabeçalhos trocados
        login(other.email, other.password, SPOOFED_PREFIX + "200").statusCode(401).body("message", is(TOO_MANY));
    }
}
