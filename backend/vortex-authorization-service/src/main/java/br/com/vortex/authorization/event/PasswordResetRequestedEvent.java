package br.com.vortex.authorization.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public class PasswordResetRequestedEvent extends AuthEvent {

    @JsonProperty("username")
    public String username;

    @JsonProperty("resetTokenId")
    public UUID resetTokenId;

    @JsonProperty("expiresAt")
    public OffsetDateTime expiresAt;

    @JsonProperty("requestMethod")
    public String requestMethod;

    public PasswordResetRequestedEvent() {
        super();
        this.eventType = "password.reset_requested";
    }

    public PasswordResetRequestedEvent(UUID userId, String userEmail, String username, UUID resetTokenId,
                                      OffsetDateTime expiresAt, String requestMethod, String ipAddress, String userAgent) {
        super("password.reset_requested", userId, userEmail, ipAddress, userAgent);
        this.username = username;
        this.resetTokenId = resetTokenId;
        this.expiresAt = expiresAt;
        this.requestMethod = requestMethod;
        this.details = Map.of(
            "resetTokenId", resetTokenId.toString(),
            "expiresAt", expiresAt.toString(),
            "requestMethod", requestMethod != null ? requestMethod : "email"
        );
    }
}