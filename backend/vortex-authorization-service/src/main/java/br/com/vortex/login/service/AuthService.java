package br.com.vortex.login.service;

import br.com.vortex.login.dto.*;
import br.com.vortex.login.exception.AuthenticationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;

@ApplicationScoped
public class AuthService {
    
    private static final Logger log = Logger.getLogger(AuthService.class.getName());
    
    @Inject
    LocalAuthService localAuthService;
    
    @Inject
    JwtService jwtService;
    
    // Local authentication methods
    public LoginResponseDTO authenticateLocal(LoginRequestDTO loginRequest) {
        return localAuthService.authenticateLocal(loginRequest);
    }
    
    public LoginResponseDTO registerUser(RegisterRequestDTO registerRequest) {
        return localAuthService.registerUser(registerRequest);
    }
    
    public void requestPasswordReset(String email) {
        localAuthService.requestPasswordReset(email);
    }
    
    public void resetPassword(ResetPasswordRequestDTO resetRequest) {
        localAuthService.resetPassword(resetRequest);
    }
    
    // OAuth methods (placeholder implementations)
    public String getAuthorizationUrl(String provider) {
        switch (provider.toLowerCase()) {
            case "google":
                return "https://accounts.google.com/oauth/authorize?client_id=placeholder&response_type=code&scope=openid%20profile%20email&redirect_uri=http://localhost:8081/auth/callback/google";
            case "github":
                return "https://github.com/login/oauth/authorize?client_id=placeholder&scope=user:email&redirect_uri=http://localhost:8081/auth/callback/github";
            default:
                throw new IllegalArgumentException("Provider não suportado: " + provider);
        }
    }
    
    public LoginResponseDTO processCallback(String provider, String code, String state) {
        // Placeholder implementation - in real scenario, this would:
        // 1. Exchange code for access token
        // 2. Get user info from provider
        // 3. Create or update user in database
        // 4. Generate JWT token
        throw new AuthenticationException("OAuth callback não implementado ainda. Use autenticação local.");
    }
    
    // JWKS methods
    public Object getJWKS() {
        return jwtService.getJWKS();
    }
    
    public Object getOpenIdConfiguration() {
        return jwtService.getOpenIdConfiguration();
    }
}