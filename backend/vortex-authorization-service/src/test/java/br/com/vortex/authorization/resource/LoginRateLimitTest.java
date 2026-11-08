package br.com.vortex.authorization.resource;

import br.com.vortex.authorization.TrustedLoopbackProxyTestProfile;
import br.com.vortex.authorization.dto.RegisterRequest;
import br.com.vortex.authorization.entity.LoginAttempt;
import br.com.vortex.authorization.util.TestDataBuilder;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Rate limit do login ponta a ponta, com o RateLimitService real. O limite (auth.rate-limit.login-attempts)
 * e a janela (auth.rate-limit.window-minutes) vêm do application.properties de teste, com os mesmos
 * valores do padrão de produção. A janela é provada com tentativas gravadas no passado, sem esperar.
 *
 * Roda com TRUSTED_PROXIES declarando o loopback (TrustedLoopbackProxyTestProfile), como faria um operador
 * atrás de um proxy reverso: por isso o X-Forwarded-For destes testes vale como IP do cliente. Sem
 * TRUSTED_PROXIES, o padrão, o cabeçalho é ignorado (UntrustedProxyLoginTest).
 */
@QuarkusTest
@TestProfile(TrustedLoopbackProxyTestProfile.class)
@TestHTTPEndpoint(AuthResource.class)
class LoginRateLimitTest {

    static final String TOO_MANY = "Too many failed login attempts. Please try again later.";
    static final String WRONG_PASSWORD = "Wrong@Password123";

    // Faixa de documentação (RFC 5737), enviada por X-Forwarded-For só por estes testes
    private static final String IP_A = "198.51.100.10";
    private static final String IP_B = "198.51.100.20";
    private static final String IP_C = "198.51.100.30";
    private static final String IP_D = "198.51.100.40";
    private static final String IP_E = "198.51.100.50";

    @Inject
    @ConfigProperty(name = "auth.rate-limit.login-attempts")
    int maxAttempts;

    @Inject
    @ConfigProperty(name = "auth.rate-limit.window-minutes")
    int windowMinutes;

    // As falhas ficam commitadas (é isso que este teste prova): limpa antes e depois para
    // não herdar tentativas de outras classes nem deixar IPs e contas bloqueados para elas
    @BeforeEach
    void clearAttemptsBefore() {
        clearAttempts();
    }

    @AfterEach
    void clearAttemptsAfter() {
        clearAttempts();
    }

    @Test
    @DisplayName("N failed logins from one IP block that IP, even with the right password and for other accounts")
    void failedLoginsFromOneIpBlockThatIp() {
        RegisterRequest victim = registerUser();
        RegisterRequest other = registerUser();

        failLogins(victim.email, IP_A, maxAttempts);

        // As falhas sobreviveram ao 401: a senha certa já não entra a partir deste IP
        login(victim.email, victim.password, IP_A).statusCode(401).body("message", is(TOO_MANY));
        // O bloqueio é do IP, não só da conta atacada
        login(other.email, other.password, IP_A).statusCode(401).body("message", is(TOO_MANY));
    }

    @Test
    @DisplayName("A different IP is not affected by another IP's failures")
    void aDifferentIpIsNotAffected() {
        RegisterRequest victim = registerUser();
        RegisterRequest other = registerUser();

        failLogins(victim.email, IP_A, maxAttempts);

        login(other.email, other.password, IP_B).statusCode(200).body("success", is(true));
        // A conta atacada segue travada de qualquer IP: o limite conta falhas por e-mail OU por IP
        login(victim.email, victim.password, IP_B).statusCode(401).body("message", is(TOO_MANY));
    }

    @Test
    @DisplayName("Failures older than the window do not count; the same failures inside it block")
    void failuresOlderThanTheWindowDoNotCount() {
        RegisterRequest user = registerUser();

        recordFailures(user.email, IP_C, maxAttempts, OffsetDateTime.now().minusMinutes(windowMinutes + 1L));
        login(user.email, user.password, IP_C).statusCode(200).body("success", is(true));

        recordFailures(user.email, IP_C, maxAttempts, OffsetDateTime.now().minusMinutes(1));
        login(user.email, user.password, IP_C).statusCode(401).body("message", is(TOO_MANY));
    }

    @Test
    @DisplayName("Attempts rejected by the limit are not recorded, so the lockout ends when the window passes")
    void blockedAttemptsAreNotRecordedAndTheLockoutExpires() {
        RegisterRequest user = registerUser();

        failLogins(user.email, IP_E, maxAttempts);
        // As falhas reais ficam a 1 minuto de sair da janela
        shiftAttempts(user.email, windowMinutes - 1L);

        // Quem insiste durante o bloqueio, até com a senha certa, é barrado e não grava falha nova.
        // Antes cada tentativa barrada virava falha e empurrava o desbloqueio para frente.
        for (int i = 0; i < maxAttempts; i++) {
            login(user.email, user.password, IP_E).statusCode(401).body("message", is(TOO_MANY));
        }
        assertEquals(maxAttempts, recordedAttempts(user.email).size(),
            "tentativa barrada pelo rate limit não pode virar falha nova");

        // Passam 2 minutos: as falhas originais saem da janela e o bloqueio acaba sozinho
        shiftAttempts(user.email, 2);
        login(user.email, user.password, IP_E).statusCode(200).body("success", is(true));
    }

    @Test
    @DisplayName("Without proxy headers the failed attempt is recorded with the socket address, not 'unknown'")
    void withoutProxyHeadersTheSocketAddressIsRecorded() throws Exception {
        RegisterRequest user = registerUser();

        login(user.email, WRONG_PASSWORD, null).statusCode(401).body("message", is("Invalid credentials"));

        List<LoginAttempt> attempts = recordedAttempts(user.email);
        assertEquals(1, attempts.size(), "a falha precisa ficar gravada mesmo com o 401");
        LoginAttempt attempt = attempts.get(0);
        assertFalse(attempt.success);
        assertNotEquals("unknown", attempt.ipAddress);
        assertTrue(InetAddress.getByName(attempt.ipAddress).isLoopbackAddress(),
            "esperado o endereço do socket (loopback no teste), veio " + attempt.ipAddress);
    }

    @Test
    @DisplayName("X-Forwarded-For from a proxy listed in TRUSTED_PROXIES is the recorded client IP")
    void forwardedClientIpFromATrustedProxyIsRecorded() {
        RegisterRequest user = registerUser();

        login(user.email, WRONG_PASSWORD, IP_D).statusCode(401).body("message", is("Invalid credentials"));

        List<LoginAttempt> attempts = recordedAttempts(user.email);
        assertEquals(1, attempts.size());
        assertEquals(IP_D, attempts.get(0).ipAddress);
    }

    @Test
    @DisplayName("A forwarded value that is not an IP literal, or is oversized, is never stored: the connection peer is")
    void invalidForwardedValueFromATrustedProxyIsNotStored() throws Exception {
        RegisterRequest user = registerUser();
        // ip_address é varchar(255): gravado como veio, o valor de 300 caracteres derrubava o insert
        // e a falha sumia (nem o limite por e-mail contava). Nome de host também não vale (pediria DNS).
        String oversized = "1".repeat(300);

        login(user.email, WRONG_PASSWORD, oversized).statusCode(401).body("message", is("Invalid credentials"));
        login(user.email, WRONG_PASSWORD, "attacker.example").statusCode(401).body("message", is("Invalid credentials"));

        List<LoginAttempt> attempts = recordedAttempts(user.email);
        assertEquals(2, attempts.size(), "as duas falhas precisam ficar gravadas");
        for (LoginAttempt attempt : attempts) {
            assertTrue(InetAddress.getByName(attempt.ipAddress).isLoopbackAddress(),
                "esperado o par real da conexão (loopback), veio " + attempt.ipAddress);
        }
    }

    @Test
    @DisplayName("X-Real-IP alone is ignored: only Quarkus' X-Forwarded-For handling sets the client IP")
    void xRealIpAloneIsIgnored() throws Exception {
        RegisterRequest user = registerUser();

        loginWithHeader(user.email, WRONG_PASSWORD, "X-Real-IP", IP_D).statusCode(401);

        List<LoginAttempt> attempts = recordedAttempts(user.email);
        assertEquals(1, attempts.size());
        assertTrue(InetAddress.getByName(attempts.get(0).ipAddress).isLoopbackAddress(),
            "esperado o endereço do socket, veio " + attempts.get(0).ipAddress);
    }

    // Auxiliares estáticos: as outras classes de login (outros perfis) usam os mesmos

    static RegisterRequest registerUser() {
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(201);
        return request;
    }

    static ValidatableResponse login(String identifier, String password, String forwardedFor) {
        return loginWithHeader(identifier, password, "X-Forwarded-For", forwardedFor);
    }

    static ValidatableResponse loginWithHeader(String identifier, String password, String header, String value) {
        RequestSpecification request = given()
            .contentType(ContentType.JSON)
            .body(TestDataBuilder.createLoginRequest(identifier, password));
        if (value != null) {
            request = request.header(header, value);
        }
        return request.when().post("/login").then();
    }

    static void failLogins(String identifier, String ip, int times) {
        for (int i = 0; i < times; i++) {
            login(identifier, WRONG_PASSWORD, ip).statusCode(401).body("message", is("Invalid credentials"));
        }
    }

    static List<LoginAttempt> recordedAttempts(String email) {
        return QuarkusTransaction.requiringNew().call(() -> LoginAttempt.<LoginAttempt>list("email", email));
    }

    static void clearAttempts() {
        QuarkusTransaction.requiringNew().run(() -> LoginAttempt.deleteAll());
    }

    // Simula a passagem do tempo: recua attempted_at de todas as tentativas da conta
    private static void shiftAttempts(String email, long minutes) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (LoginAttempt attempt : LoginAttempt.<LoginAttempt>list("email", email)) {
                attempt.attemptedAt = attempt.attemptedAt.minusMinutes(minutes);
            }
        });
    }

    private static void recordFailures(String email, String ip, int times, OffsetDateTime attemptedAt) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (int i = 0; i < times; i++) {
                LoginAttempt attempt = new LoginAttempt();
                attempt.email = email;
                attempt.ipAddress = ip;
                attempt.success = false;
                attempt.attemptedAt = attemptedAt;
                attempt.persist();
            }
        });
    }
}
