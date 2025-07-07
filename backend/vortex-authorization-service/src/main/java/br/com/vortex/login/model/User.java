package br.com.vortex.login.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {
    
    @Column(unique = true, nullable = false)
    @Email(message = "Email deve ter formato válido")
    @NotBlank(message = "Email é obrigatório")
    public String email;
    
    @Column(nullable = false)
    @NotBlank(message = "Nome é obrigatório")
    public String name;
    
    @Column(name = "password_hash")
    public String passwordHash; // Para login local
    
    @Column(name = "provider")
    @NotNull(message = "Provider é obrigatório")
    public String provider; // google, github, local
    
    @Column(name = "provider_id")
    public String providerId;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    public Set<Role> roles = new HashSet<>();
    
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    
    @Column(name = "deleted_at")
    public LocalDateTime deletedAt;
    
    @Column(name = "last_login")
    public LocalDateTime lastLogin;
    
    @Column(name = "login_count")
    public Long loginCount = 0L;
    
    @Column(name = "email_verified")
    public Boolean emailVerified = false;
    
    @Column(name = "reset_token")
    public String resetToken;
    
    @Column(name = "reset_token_expiry")
    public LocalDateTime resetTokenExpiry;
    
    @Column(name = "account_locked")
    public Boolean accountLocked = false;
    
    @Column(name = "failed_login_attempts")
    public Integer failedLoginAttempts = 0;
    
    public enum Role {
        USER, ADMIN, MANAGER
    }
    
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (roles.isEmpty()) {
            roles.add(Role.USER);
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public static User findByEmail(String email) {
        return find("email = ?1 and deletedAt is null", email).firstResult();
    }
    
    public static User findByProviderAndProviderId(String provider, String providerId) {
        return find("provider = ?1 and providerId = ?2 and deletedAt is null", provider, providerId).firstResult();
    }
    
    public static User findByResetToken(String token) {
        return find("resetToken = ?1 and resetTokenExpiry > ?2 and deletedAt is null", 
                   token, LocalDateTime.now()).firstResult();
    }
    
    public boolean isAccountLocked() {
        return accountLocked != null && accountLocked;
    }
    
    public boolean isResetTokenValid() {
        return resetToken != null && resetTokenExpiry != null && 
               resetTokenExpiry.isAfter(LocalDateTime.now());
    }
}