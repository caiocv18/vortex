package br.com.vortex.authorization.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens", schema = "auth")
public class PasswordResetToken extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(name = "token", nullable = false, unique = true)
    public String token;

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;

    @Column(name = "used")
    public Boolean used = false;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (used == null) {
            used = false;
        }
    }

    public static PasswordResetToken findByToken(String token) {
        return find("token", token).firstResult();
    }

    public static PasswordResetToken findValidToken(String token) {
        return find("token = ?1 AND used = false AND expiresAt > ?2", 
                   token, OffsetDateTime.now()).firstResult();
    }

    public static void expireAllUserTokens(UUID userId) {
        update("used = true WHERE user.id = ?1 AND used = false", userId);
    }

    public boolean isValid() {
        return !Boolean.TRUE.equals(used) && 
               expiresAt != null && 
               expiresAt.isAfter(OffsetDateTime.now());
    }

    public void markAsUsed() {
        used = true;
    }
}