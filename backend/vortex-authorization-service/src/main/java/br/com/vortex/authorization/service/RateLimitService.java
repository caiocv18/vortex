package br.com.vortex.authorization.service;

import br.com.vortex.authorization.entity.LoginAttempt;
import jakarta.enterprise.context.ApplicationScoped;
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