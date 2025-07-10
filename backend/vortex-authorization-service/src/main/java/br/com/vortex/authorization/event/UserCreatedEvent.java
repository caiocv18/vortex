package br.com.vortex.authorization.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserCreatedEvent extends AuthEvent {

    @JsonProperty("username")
    public String username;

    @JsonProperty("roles")
    public Set<String> roles;

    @JsonProperty("isVerified")
    public Boolean isVerified;

    @JsonProperty("registrationMethod")
    public String registrationMethod;

    public UserCreatedEvent() {
        super();
        this.eventType = "user.created";
    }

    public UserCreatedEvent(UUID userId, String userEmail, String username, Set<String> roles, 
                           Boolean isVerified, String registrationMethod, String ipAddress, String userAgent) {
        super("user.created", userId, userEmail, ipAddress, userAgent);
        this.username = username;
        this.roles = roles;
        this.isVerified = isVerified;
        this.registrationMethod = registrationMethod;
        this.details = Map.of(
            "registrationMethod", registrationMethod,
            "rolesCount", roles.size(),
            "isVerified", isVerified
        );
    }
}