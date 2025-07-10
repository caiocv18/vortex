package br.com.vortex.authorization.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "login_attempts", schema = "auth")
public class LoginAttempt extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "email")
    public String email;

    @Column(name = "ip_address", nullable = false)
    public String ipAddress;

    @Column(name = "success")
    public Boolean success = false;

    @Column(name = "attempted_at")
    public OffsetDateTime attemptedAt;

    @PrePersist
    public void prePersist() {
        if (attemptedAt == null) {
            attemptedAt = OffsetDateTime.now();
        }
        if (success == null) {
            success = false;
        }
    }

    public static long countRecentFailedAttempts(String email, String ipAddress, OffsetDateTime since) {
        return count("(email = ?1 OR ipAddress = ?2) AND success = false AND attemptedAt > ?3", 
                    email, ipAddress, since);
    }

    public static long countRecentFailedAttemptsByEmail(String email, OffsetDateTime since) {
        return count("email = ?1 AND success = false AND attemptedAt > ?2", email, since);
    }

    public static long countRecentFailedAttemptsByIp(String ipAddress, OffsetDateTime since) {
        return count("ipAddress = ?1 AND success = false AND attemptedAt > ?2", ipAddress, since);
    }

    public static void recordAttempt(String email, String ipAddress, boolean success) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.email = email;
        attempt.ipAddress = ipAddress;
        attempt.success = success;
        attempt.persist();
    }
}