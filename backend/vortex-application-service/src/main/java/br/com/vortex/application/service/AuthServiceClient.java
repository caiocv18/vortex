package br.com.vortex.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Service
public class AuthServiceClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;
    
    public AuthServiceClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
    
    public Map<String, Object> validateToken(String token) {
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
            logger.error("Error validating token with auth service: ", e);
            return null;
        }
    }
    
    public Map<String, Object> getUserInfo(String token) {
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
            logger.error("Error getting user info from auth service: ", e);
            return null;
        }
    }
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthServiceClient.class);
}