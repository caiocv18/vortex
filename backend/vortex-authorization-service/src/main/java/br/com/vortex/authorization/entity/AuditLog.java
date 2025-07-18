package br.com.vortex.authorization.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", schema = "auth")
public class AuditLog extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    public User user;

    @Column(name = "action", nullable = false)
    public String action;

    @Column(name = "details")
    @JdbcTypeCode(SqlTypes.JSON)
    public String details;

    @Column(name = "ip_address")
    public String ipAddress;

    @Column(name = "user_agent")
    public String userAgent;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public static void log(User user, String action, Map<String, Object> details, String ipAddress, String userAgent) {
        AuditLog log = new AuditLog();
        log.user = user;
        log.action = action;
        try {
            if (details != null) {
                ObjectMapper mapper = new ObjectMapper();
                log.details = mapper.writeValueAsString(details);
            } else {
                log.details = null;
            }
        } catch (Exception e) {
            log.details = "{}"; // fallback to empty JSON object
        }
        log.ipAddress = ipAddress;
        log.userAgent = userAgent;
        log.persist();
    }

    public static void log(UUID userId, String action, Map<String, Object> details, String ipAddress, String userAgent) {
        User user = userId != null ? User.findById(userId) : null;
        log(user, action, details, ipAddress, userAgent);
    }
}