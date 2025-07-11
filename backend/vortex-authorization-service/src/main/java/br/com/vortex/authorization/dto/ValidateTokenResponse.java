package br.com.vortex.authorization.dto;

import java.util.List;

public class ValidateTokenResponse {
    
    private boolean valid;
    private String username;
    private String email;
    private List<String> roles;
    private String userId;
    
    public ValidateTokenResponse() {}
    
    public ValidateTokenResponse(boolean valid, String username, String email, List<String> roles, String userId) {
        this.valid = valid;
        this.username = username;
        this.email = email;
        this.roles = roles;
        this.userId = userId;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public List<String> getRoles() {
        return roles;
    }
    
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
}