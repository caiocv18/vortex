package br.com.vortex.application.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.HashMap;

/**
 * Endpoints de diagnóstico, abertos sem token fora do perfil {@code cloud}. Na demo pública o controller nem
 * existe, e a cadeia de segurança ainda nega {@code /api/test/**}.
 */
@RestController
@RequestMapping("/api/test")
@Profile("!cloud")
public class TestController {
    
    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "This is a public endpoint - no auth required");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/auth-info")
    public ResponseEntity<Map<String, Object>> authInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();
        response.put("hasAuthHeader", authHeader != null);
        response.put("authHeaderValue", authHeader != null ? authHeader.substring(0, Math.min(authHeader.length(), 50)) + "..." : null);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ResponseEntity.ok(response);
    }
}