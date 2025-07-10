package br.com.vortex.authorization.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

public class UserLoggedOutEvent extends AuthEvent {

    @JsonProperty("username")
    public String username;

    @JsonProperty("sessionId")
    public String sessionId;

    @JsonProperty("logoutReason")
    public String logoutReason;

    public UserLoggedOutEvent() {
        super();
        this.eventType = "user.logged_out";
    }

    public UserLoggedOutEvent(UUID userId, String userEmail, String username, String sessionId,
                             String logoutReason, String ipAddress, String userAgent) {
        super("user.logged_out", userId, userEmail, ipAddress, userAgent);
        this.username = username;
        this.sessionId = sessionId;
        this.logoutReason = logoutReason;
        this.details = Map.of(
            "sessionId", sessionId != null ? sessionId : "unknown",
            "logoutReason", logoutReason != null ? logoutReason : "user_initiated"
        );
    }
}