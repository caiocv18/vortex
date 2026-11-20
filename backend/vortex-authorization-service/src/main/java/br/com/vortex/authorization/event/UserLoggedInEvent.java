package br.com.vortex.authorization.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserLoggedInEvent extends AuthEvent {

    @JsonProperty("username")
    public String username;

    @JsonProperty("roles")
    public Set<String> roles;

    @JsonProperty("loginMethod")
    public String loginMethod;

    @JsonProperty("lastLogin")
    public OffsetDateTime lastLogin;

    @JsonProperty("sessionId")
    public String sessionId;

    public UserLoggedInEvent() {
        super();
        this.eventType = "user.logged_in";
    }

    public UserLoggedInEvent(UUID userId, String userEmail, String username, Set<String> roles,
                            LoginSession session, RequestOrigin origin) {
        super("user.logged_in", userId, userEmail, origin.ipAddress(), origin.userAgent());
        this.username = username;
        this.roles = roles;
        this.loginMethod = session.method();
        this.lastLogin = session.at();
        this.sessionId = session.sessionId();
        this.details = Map.of(
            "loginMethod", loginMethod,
            "rolesCount", roles.size(),
            "sessionId", sessionId != null ? sessionId : "unknown"
        );
    }
}