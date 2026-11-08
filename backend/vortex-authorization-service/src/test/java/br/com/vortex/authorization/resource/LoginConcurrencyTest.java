package br.com.vortex.authorization.resource;

import br.com.vortex.authorization.dto.RegisterRequest;
import br.com.vortex.authorization.entity.LoginAttempt;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static br.com.vortex.authorization.resource.LoginRateLimitTest.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Logins com senha errada ao mesmo tempo, tantos quanto as conexões do pool do modo leve (5).
 *
 * Antes a falha era gravada num REQUIRES_NEW: cada login segurava a conexão da própria transação (a da
 * contagem do rate limit) e pedia uma segunda para gravar a falha. Com 5 logins assim nenhum conseguia
 * a segunda, todos esperavam o acquisition-timeout e a resposta era 401 com o texto do pool ("Unable to
 * acquire JDBC Connection"), sem gravar a falha. Agora a falha entra no commit do próprio login.
 *
 * O pool da suíte inteira tem o tamanho do leve (src/test/resources/application.properties), então este
 * teste roda no app padrão de teste, sem perfil próprio.
 */
@QuarkusTest
@TestHTTPEndpoint(AuthResource.class)
class LoginConcurrencyTest {

    // %leve.quarkus.datasource.jdbc.max-size no application.properties principal
    private static final int LEVE_POOL_SIZE = 5;

    @Inject
    @ConfigProperty(name = "quarkus.datasource.jdbc.max-size")
    int poolSize;

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
    @DisplayName("As many concurrent failed logins as pool connections are all answered and all recorded")
    void concurrentFailedLoginsDoNotStarveThePool() throws Exception {
        assertEquals(LEVE_POOL_SIZE, poolSize, "o teste só prova algo com o pool do tamanho do leve");
        // Com 5 requisições e limite de 5, nenhuma pode ser barrada pelo rate limit (cada uma vê no
        // máximo as 4 falhas das outras) e as 5 juntas bastam para bloquear o IP
        assertEquals(maxAttempts, poolSize);

        List<RegisterRequest> users = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            users.add(registerUser());
        }

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CyclicBarrier start = new CyclicBarrier(poolSize);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (RegisterRequest user : users) {
                results.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    ExtractableResponse<Response> response = login(user.email, WRONG_PASSWORD, null).extract();
                    return response.statusCode() + " " + response.path("message");
                }));
            }
            for (Future<String> result : results) {
                assertEquals("401 Invalid credentials", result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        long failures = QuarkusTransaction.requiringNew().call(() -> LoginAttempt.count("success = false"));
        assertEquals(poolSize, failures, "toda falha concorrente precisa ficar gravada");

        // E elas valem para o bloqueio: a senha certa já não entra a partir deste IP
        RegisterRequest any = users.get(0);
        login(any.email, any.password, null).statusCode(401).body("message", is(TOO_MANY));
    }
}
