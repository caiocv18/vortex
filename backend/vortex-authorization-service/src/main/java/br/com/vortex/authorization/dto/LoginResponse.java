package br.com.vortex.authorization.dto;

import java.time.OffsetDateTime;
import java.util.Set;

public class LoginResponse {

    public String accessToken;
    public String refreshToken;
    public String tokenType = "Bearer";
    public Long expiresIn;
    public UserResponse user;

    public static class UserResponse {
        public String id;
        public String email;
        public String username;
        public Set<String> roles;
        public OffsetDateTime lastLogin;
        public boolean isActive;
        public boolean isVerified;
    }
}