package br.com.vortex.login.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ApplicationScoped
public class PasswordService {
    
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    public String hashPassword(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }
    
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        return passwordEncoder.matches(plainPassword, hashedPassword);
    }
}