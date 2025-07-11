package br.com.vortex.authorization.dto;

import jakarta.validation.constraints.NotBlank;

public class ValidateTokenRequest {
    
    @NotBlank(message = "Token is required")
    private String token;
    
    public ValidateTokenRequest() {}
    
    public ValidateTokenRequest(String token) {
        this.token = token;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
}