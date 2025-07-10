package br.com.vortex.authorization.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

public class EmailVerifiedEvent extends AuthEvent {

    @JsonProperty("username")
    public String username;

    @JsonProperty("verificationMethod")
    public String verificationMethod;

    @JsonProperty("verificationTokenId")
    public UUID verificationTokenId;

    public EmailVerifiedEvent() {
        super();
        this.eventType = "email.verified";
    }

    public EmailVerifiedEvent(UUID userId, String userEmail, String username, String verificationMethod,
                             UUID verificationTokenId, String ipAddress, String userAgent) {
        super("email.verified", userId, userEmail, ipAddress, userAgent);
        this.username = username;
        this.verificationMethod = verificationMethod;
        this.verificationTokenId = verificationTokenId;
        this.details = Map.of(
            "verificationMethod", verificationMethod != null ? verificationMethod : "email_link",
            "verificationTokenId", verificationTokenId != null ? verificationTokenId.toString() : "unknown"
        );
    }
}