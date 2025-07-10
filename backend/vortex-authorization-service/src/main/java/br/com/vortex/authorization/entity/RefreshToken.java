package br.com.vortex.authorization.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", schema = "auth")
public class RefreshToken extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public User user;

    @Column(name = "token", nullable = false, unique = true, length = 1000)
    public String token;

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;

    @Column(name = "revoked")
    public Boolean revoked = false;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (revoked == null) {
            revoked = false;
        }
    }

    public static RefreshToken findByToken(String token) {
        return find("token", token).firstResult();
    }

    public static RefreshToken findValidToken(String token) {
        return find("token = ?1 AND revoked = false AND expiresAt > ?2", 
                   token, OffsetDateTime.now()).firstResult();
    }

    public static void revokeAllUserTokens(UUID userId) {
        update("revoked = true WHERE user.id = ?1 AND revoked = false", userId);
    }

    public boolean isValid() {
        return !Boolean.TRUE.equals(revoked) && 
               expiresAt != null && 
               expiresAt.isAfter(OffsetDateTime.now());
    }

    public void revoke() {
        revoked = true;
    }
}