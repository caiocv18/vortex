package br.com.vortex.authorization.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public abstract class AuthEvent {

    @JsonProperty("eventId")
    public UUID eventId;

    @JsonProperty("eventType")
    public String eventType;

    @JsonProperty("userId")
    public UUID userId;

    @JsonProperty("userEmail")
    public String userEmail;

    @JsonProperty("timestamp")
    public OffsetDateTime timestamp;

    @JsonProperty("ipAddress")
    public String ipAddress;

    @JsonProperty("userAgent")
    public String userAgent;

    @JsonProperty("details")
    public Map<String, Object> details;

    @JsonProperty("version")
    public String version = "1.0";

    public AuthEvent() {
        this.eventId = UUID.randomUUID();
        this.timestamp = OffsetDateTime.now();
    }

    public AuthEvent(String eventType, UUID userId, String userEmail, String ipAddress, String userAgent) {
        this();
        this.eventType = eventType;
        this.userId = userId;
        this.userEmail = userEmail;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}