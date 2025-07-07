package br.com.vortex.login.service;

import br.com.vortex.login.dto.*;
import br.com.vortex.login.model.User;
import br.com.vortex.login.exception.AuthenticationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@ApplicationScoped
public class LocalAuthService {
    
    private static final Logger log = Logger.getLogger(LocalAuthService.class.getName());
    
    @Inject
    PasswordService passwordService;
    
    @Inject
    JwtService jwtService;
    
    @Inject
    EmailService emailService;
    
    @ConfigProperty(name = "auth.password.min-length", defaultValue = "8")
    int minPasswordLength;
    
    @ConfigProperty(name = "auth.password-reset.token-expiry-minutes", defaultValue = "30")
    int resetTokenExpiryMinutes;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!?*._-]).*$");
    
    @Transactional
    public LoginResponseDTO authenticateLocal(LoginRequestDTO loginRequest) {
        User user = User.findByEmail(loginRequest.getEmail());
        
        if (user == null || !user.provider.equals("local")) {
            throw new AuthenticationException("Credenciais inválidas");
        }
        
        if (user.isAccountLocked()) {
            throw new AuthenticationException("Conta bloqueada. Entre em contato com o suporte.");
        }
        
        if (!passwordService.verifyPassword(loginRequest.getPassword(), user.passwordHash)) {
            user.failedLoginAttempts++;
            if (user.failedLoginAttempts >= 5) {
                user.accountLocked = true;
                log.warning("Conta bloqueada por tentativas excessivas: " + user.email);
            }
            user.persist();
            throw new AuthenticationException("Credenciais inválidas");
        }
        
        // Reset failed attempts on successful login
        user.failedLoginAttempts = 0;
        user.lastLogin = LocalDateTime.now();
        user.loginCount++;
        user.persist();
        
        String token = jwtService.generateToken(user);
        
        return createLoginResponse(user, token);
    }
    
    @Transactional
    public LoginResponseDTO registerUser(RegisterRequestDTO registerRequest) {
        validateRegistration(registerRequest);
        
        if (User.findByEmail(registerRequest.getEmail()) != null) {
            throw new IllegalArgumentException("Email já está em uso");
        }
        
        User user = new User();
        user.email = registerRequest.getEmail();
        user.name = registerRequest.getName();
        user.provider = "local";
        user.passwordHash = passwordService.hashPassword(registerRequest.getPassword());
        user.emailVerified = false; // Pode implementar verificação por email
        user.persist();
        
        String token = jwtService.generateToken(user);
        
        return createLoginResponse(user, token);
    }
    
    @Transactional
    public void requestPasswordReset(String email) {
        User user = User.findByEmail(email);
        
        if (user == null || !user.provider.equals("local")) {
            log.info("Tentativa de reset para email não encontrado ou não local: " + email);
            return; // Não revelar se email existe
        }
        
        String resetToken = generateResetToken();
        user.resetToken = resetToken;
        user.resetTokenExpiry = LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes);
        user.persist();
        
        emailService.sendPasswordResetEmail(user.email, user.name, resetToken);
        log.info("Email de recuperação enviado para: " + email);
    }
    
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO resetRequest) {
        if (!resetRequest.getNewPassword().equals(resetRequest.getConfirmPassword())) {
            throw new IllegalArgumentException("Senhas não coincidem");
        }
        
        validatePassword(resetRequest.getNewPassword());
        
        User user = User.findByResetToken(resetRequest.getToken());
        
        if (user == null) {
            throw new IllegalArgumentException("Token inválido ou expirado");
        }
        
        user.passwordHash = passwordService.hashPassword(resetRequest.getNewPassword());
        user.resetToken = null;
        user.resetTokenExpiry = null;
        user.failedLoginAttempts = 0;
        user.accountLocked = false;
        user.persist();
        
        log.info("Senha redefinida com sucesso para usuário: " + user.email);
    }
    
    private void validateRegistration(RegisterRequestDTO registerRequest) {
        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            throw new IllegalArgumentException("Senhas não coincidem");
        }
        
        if (!EMAIL_PATTERN.matcher(registerRequest.getEmail()).matches()) {
            throw new IllegalArgumentException("Email inválido");
        }
        
        validatePassword(registerRequest.getPassword());
    }
    
    private void validatePassword(String password) {
        if (password.length() < minPasswordLength) {
            throw new IllegalArgumentException("Senha deve ter pelo menos " + minPasswordLength + " caracteres");
        }
        
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Senha deve conter pelo menos uma letra minúscula, uma maiúscula, um número e um caractere especial");
        }
    }
    
    private String generateResetToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private LoginResponseDTO createLoginResponse(User user, String token) {
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setUser(convertToUserDTO(user));
        response.setExpiresIn(8 * 60 * 60L); // 8 horas em segundos
        return response;
    }
    
    private UserDTO convertToUserDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.id);
        userDTO.setEmail(user.email);
        userDTO.setName(user.name);
        userDTO.setProvider(user.provider);
        userDTO.setRoles(user.roles.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        userDTO.setLastLogin(user.lastLogin);
        userDTO.setEmailVerified(user.emailVerified);
        return userDTO;
    }
}