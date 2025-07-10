package br.com.vortex.authorization.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

public class PasswordChangedEvent extends AuthEvent {

    @JsonProperty("username")
    public String username;

    @JsonProperty("changeReason")
    public String changeReason;

    @JsonProperty("changeMethod")
    public String changeMethod;

    public PasswordChangedEvent() {
        super();
        this.eventType = "password.changed";
    }

    public PasswordChangedEvent(UUID userId, String userEmail, String username, String changeReason,
                               String changeMethod, String ipAddress, String userAgent) {
        super("password.changed", userId, userEmail, ipAddress, userAgent);
        this.username = username;
        this.changeReason = changeReason;
        this.changeMethod = changeMethod;
        this.details = Map.of(
            "changeReason", changeReason != null ? changeReason : "user_request",
            "changeMethod", changeMethod != null ? changeMethod : "manual"
        );
    }
}