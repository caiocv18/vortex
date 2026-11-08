package br.com.vortex.authorization.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * Marca o token como usado só se ele ainda estiver válido, numa única instrução UPDATE.
     * O banco serializa as atualizações concorrentes da mesma linha, então só uma transação
     * recebe 1 linha afetada; as demais recebem 0 e devem tratar o token como inválido.
     */
    public static boolean consume(UUID tokenId) {
        return update("used = true WHERE id = ?1 AND used = false AND expiresAt > ?2",
                      tokenId, OffsetDateTime.now()) == 1;
    }

    @Transactional
    public static void expireAllUserTokens(UUID userId) {
        List<PasswordResetToken> tokens = list("user.id = ?1 AND used = false", userId);
        for (PasswordResetToken token : tokens) {
            token.used = true;
            token.persist();
        }
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