package br.com.vortex.authorization.service;

import br.com.vortex.authorization.entity.LoginAttempt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;

@ApplicationScoped
public class RateLimitService {

    @ConfigProperty(name = "auth.rate-limit.login-attempts")
    int maxLoginAttempts;

    @ConfigProperty(name = "auth.rate-limit.window-minutes")
    int windowMinutes;

    @ConfigProperty(name = "auth.rate-limit.lockout-minutes")
    int lockoutMinutes;

    /**
     * Grava a tentativa de login falha numa transação própria. O login lança 401 (ou 400 no
     * bloqueio) logo depois e a transação dele faz rollback: gravada nela, a falha sumia junto
     * e o limite de tentativas nunca era atingido.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailedAttempt(String identifier, String ipAddress) {
        LoginAttempt.recordAttempt(identifier, ipAddress, false);
    }

    public boolean isAllowed(String email, String ipAddress) {
        OffsetDateTime windowStart = OffsetDateTime.now().minusMinutes(windowMinutes);
        
        long recentFailedAttempts = LoginAttempt.countRecentFailedAttempts(email, ipAddress, windowStart);
        
        return recentFailedAttempts < maxLoginAttempts;
    }

    public boolean isEmailBlocked(String email) {
        OffsetDateTime windowStart = OffsetDateTime.now().minusMinutes(lockoutMinutes);
        
        long recentFailedAttempts = LoginAttempt.countRecentFailedAttemptsByEmail(email, windowStart);
        
        return recentFailedAttempts >= maxLoginAttempts;
    }

    public boolean isIpBlocked(String ipAddress) {
        OffsetDateTime windowStart = OffsetDateTime.now().minusMinutes(lockoutMinutes);
        
        long recentFailedAttempts = LoginAttempt.countRecentFailedAttemptsByIp(ipAddress, windowStart);
        
        return recentFailedAttempts >= maxLoginAttempts;
    }
}