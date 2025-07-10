package br.com.vortex.authorization.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "auth")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "id")
    public UUID id;

    @Column(name = "email", nullable = false, unique = true)
    public String email;

    @Column(name = "username", nullable = false, unique = true)
    public String username;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @Column(name = "last_login")
    public OffsetDateTime lastLogin;

    @Column(name = "is_active")
    public Boolean isActive = true;

    @Column(name = "is_verified")
    public Boolean isVerified = false;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public Credential credential;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "user_roles",
        schema = "auth",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    public Set<Role> roles;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public Set<RefreshToken> refreshTokens;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public Set<PasswordResetToken> passwordResetTokens;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public Set<AuditLog> auditLogs;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (isVerified == null) {
            isVerified = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }

    public static User findByEmailOrUsername(String identifier) {
        return find("email = ?1 OR username = ?1", identifier).firstResult();
    }

    public boolean hasRole(String roleName) {
        return roles != null && roles.stream()
            .anyMatch(role -> roleName.equals(role.name));
    }

    public boolean isActiveAndVerified() {
        return Boolean.TRUE.equals(isActive) && Boolean.TRUE.equals(isVerified);
    }
}