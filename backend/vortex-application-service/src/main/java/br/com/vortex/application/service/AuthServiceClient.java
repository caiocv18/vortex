package br.com.vortex.application.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class AuthServiceClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() { };
    private static final String LOCAL_VALIDATION = "local-validation";
    private static final String LOCAL_TOKEN_EXTRACTION = "local-token-extraction";

    private final RestClient restClient;
    private final AuthTokenVerifier authTokenVerifier;
    private final ExecutorService executor;

    /**
     * @param restClientBuilder builder auto-configurado pelo Boot (conversores com o JsonMapper da aplicação e a
     *                          observação HTTP, que gera o span de cliente e o cabeçalho {@code traceparent})
     * @param authTokenVerifier validação local usada nos fallbacks (chave pública RS256 do serviço de autorização)
     * @param authServiceUrl URL base do serviço de autorização
     */
    public AuthServiceClient(RestClient.Builder restClientBuilder, AuthTokenVerifier authTokenVerifier,
                             @Value("${auth.service.url:http://localhost:8081}") String authServiceUrl) {
        // A URL base fica no builder e cada chamada passa só o caminho: o caminho vira o nome do span e a tag uri
        // da métrica http.client.requests, sem host nem porta.
        this.restClient = restClientBuilder
                .baseUrl(authServiceUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)))
                .build();
        this.authTokenVerifier = authTokenVerifier;
        this.executor = contextPropagatingExecutor();
    }

    /**
     * Executor dos {@code supplyAsync}. Sem executor, o {@code supplyAsync} roda no {@code ForkJoinPool.commonPool()},
     * que não conhece a observação do thread do servlet: a chamada ao auth abriria um trace novo, partido do trace
     * da requisição. O {@link ContextExecutorService} captura o contexto (a observação corrente e, com ela, o span)
     * no thread que submete e o restaura no thread que executa. Threads virtuais: a tarefa só espera rede.
     */
    static ExecutorService contextPropagatingExecutor() {
        ContextSnapshotFactory snapshots = ContextSnapshotFactory.builder().build();
        return ContextExecutorService.wrap(Executors.newVirtualThreadPerTaskExecutor(), () -> snapshots.captureAll());
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }

    @CircuitBreaker(name = "auth-service", fallbackMethod = "validateTokenFallback")
    @Retry(name = "auth-service")
    @TimeLimiter(name = "auth-service")
    public CompletableFuture<Map<String, Object>> validateToken(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use the alternative endpoint that doesn't trigger automatic JWT validation
                ResponseEntity<Map<String, Object>> response = restClient.post()
                        .uri("/api/auth/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", token))
                        .retrieve()
                        .toEntity(JSON_OBJECT);

                if (response.getStatusCode() == HttpStatus.OK) {
                    return response.getBody();
                }

                return null;
            } catch (Exception e) {
                log.error("Error validating token with auth service: ", e);
                throw new RuntimeException("Auth service validation failed", e);
            }
        }, executor);
    }

    /**
     * Fallback do validate-token (erro de transporte, 4xx/5xx, timeout ou circuito aberto): confere o token
     * localmente com a chave pública RS256 do serviço de autorização ({@link AuthTokenVerifier}). Responde
     * no mesmo formato do validate-token ({@code username}, {@code email}, {@code roles}, {@code userId}).
     * Sem chave configurada, ou com qualquer erro, o token é inválido.
     */
    public CompletableFuture<Map<String, Object>> validateTokenFallback(String token, Exception ex) {
        log.warn("Auth service unavailable, validating the token locally with the auth public key. Error: {}",
                ex.getMessage());

        return CompletableFuture.supplyAsync(() -> {
            try {
                return authTokenVerifier.verify(token)
                        .map(verified -> {
                            Map<String, Object> response = userFields(verified, LOCAL_VALIDATION);
                            response.put("valid", true);
                            return response;
                        })
                        .orElseGet(() -> Map.of("valid", false, "source", LOCAL_VALIDATION));
            } catch (Exception e) {
                log.error("Local token validation failed: {}", e.getMessage());
                return Map.of("valid", false, "source", LOCAL_VALIDATION);
            }
        }, executor);
    }

    @CircuitBreaker(name = "auth-service", fallbackMethod = "getUserInfoFallback")
    @Retry(name = "auth-service")
    @TimeLimiter(name = "auth-service")
    public CompletableFuture<Map<String, Object>> getUserInfo(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<Map<String, Object>> response = restClient.get()
                        .uri("/auth/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .toEntity(JSON_OBJECT);

                if (response.getStatusCode() == HttpStatus.OK) {
                    return response.getBody();
                }

                return null;
            } catch (Exception e) {
                log.error("Error getting user info from auth service: ", e);
                throw new RuntimeException("Auth service user info failed", e);
            }
        }, executor);
    }

    /** Fallback do userinfo: os dados vêm do próprio token, conferido como em {@link #validateTokenFallback}. */
    public CompletableFuture<Map<String, Object>> getUserInfoFallback(String token, Exception ex) {
        log.warn("Auth service unavailable for getUserInfo, using the locally verified token. Error: {}",
                ex.getMessage());

        return CompletableFuture.supplyAsync(() -> {
            try {
                return authTokenVerifier.verify(token)
                        .map(verified -> userFields(verified, LOCAL_TOKEN_EXTRACTION))
                        .orElseGet(() -> Map.of("error", "Invalid token", "source", LOCAL_TOKEN_EXTRACTION));
            } catch (Exception e) {
                log.error("Local token extraction failed: {}", e.getMessage());
                return Map.of("error", "Invalid token", "source", LOCAL_TOKEN_EXTRACTION);
            }
        }, executor);
    }

    /** Campos do usuário no formato do validate-token; o filtro usa o email quando falta o username. */
    private static Map<String, Object> userFields(AuthTokenVerifier.VerifiedToken verified, String source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (verified.username() != null) {
            fields.put("username", verified.username());
        }
        fields.put("email", verified.email());
        fields.put("roles", verified.roles());
        fields.put("userId", verified.userId());
        fields.put("source", source);
        return fields;
    }
}
