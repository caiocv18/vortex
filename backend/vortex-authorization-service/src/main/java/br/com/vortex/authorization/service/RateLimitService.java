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
     * Grava a tentativa de login falha na transação de quem chama (MANDATORY: sem transação, falha).
     *
     * O AuthService.login lança 401 logo depois e faz commit mesmo assim (dontRollbackOn), então a
     * falha sobrevive ao 401 sem precisar de transação própria. A versão anterior usava REQUIRES_NEW:
     * a transação do login, suspensa, continuava com a conexão dela, e a nova pedia uma segunda ao pool.
     * Com logins errados simultâneos no tamanho do pool, todos esperavam a segunda até o timeout.
     */
    @Transactional(Transactional.TxType.MANDATORY)
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