package br.com.vortex.application.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class AuthServiceClient {
    
    private final RestTemplate restTemplate;
    private final JwtService jwtService;
    
    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;
    
    public AuthServiceClient(RestTemplateBuilder restTemplateBuilder, JwtService jwtService) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.jwtService = jwtService;
    }
    
    @CircuitBreaker(name = "auth-service", fallbackMethod = "validateTokenFallback")
    @Retry(name = "auth-service")
    @TimeLimiter(name = "auth-service")
    public CompletableFuture<Map<String, Object>> validateToken(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use the alternative endpoint that doesn't trigger automatic JWT validation
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                Map<String, String> requestBody = Map.of("token", token);
                HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                        authServiceUrl + "/api/auth/validate-token",
                        HttpMethod.POST,
                        entity,
                        Map.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return response.getBody();
                }
                
                return null;
            } catch (Exception e) {
                log.error("Error validating token with auth service: ", e);
                throw new RuntimeException("Auth service validation failed", e);
            }
        });
    }

    public CompletableFuture<Map<String, Object>> validateTokenFallback(String token, Exception ex) {
        log.warn("Auth service circuit breaker activated, using local validation for token. Error: {}", ex.getMessage());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Local JWT validation as fallback
                if (jwtService.validateToken(token)) {
                    String username = jwtService.extractUsername(token);
                    String role = jwtService.extractRole(token);
                    String userId = jwtService.extractUserId(token);
                    
                    return Map.of(
                        "valid", true,
                        "username", username != null ? username : "unknown",
                        "role", role != null ? role : "USER",
                        "userId", userId != null ? userId : "0",
                        "source", "local-validation"
                    );
                }
                return Map.of("valid", false, "source", "local-validation");
            } catch (Exception e) {
                log.error("Local token validation failed: {}", e.getMessage());
                return Map.of("valid", false, "error", e.getMessage(), "source", "local-validation");
            }
        });
    }
    
    @CircuitBreaker(name = "auth-service", fallbackMethod = "getUserInfoFallback")
    @Retry(name = "auth-service")
    @TimeLimiter(name = "auth-service")
    public CompletableFuture<Map<String, Object>> getUserInfo(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                        authServiceUrl + "/auth/userinfo",
                        HttpMethod.GET,
                        entity,
                        Map.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    return response.getBody();
                }
                
                return null;
            } catch (Exception e) {
                log.error("Error getting user info from auth service: ", e);
                throw new RuntimeException("Auth service user info failed", e);
            }
        });
    }

    public CompletableFuture<Map<String, Object>> getUserInfoFallback(String token, Exception ex) {
        log.warn("Auth service circuit breaker activated for getUserInfo, using local token data. Error: {}", ex.getMessage());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Extract info from token as fallback
                if (jwtService.validateToken(token)) {
                    String username = jwtService.extractUsername(token);
                    String role = jwtService.extractRole(token);
                    String userId = jwtService.extractUserId(token);
                    
                    return Map.of(
                        "username", username != null ? username : "unknown",
                        "role", role != null ? role : "USER",
                        "userId", userId != null ? userId : "0",
                        "source", "local-token-extraction"
                    );
                }
                return Map.of("error", "Invalid token", "source", "local-token-extraction");
            } catch (Exception e) {
                log.error("Local token extraction failed: {}", e.getMessage());
                return Map.of("error", e.getMessage(), "source", "local-token-extraction");
            }
        });
    }
}