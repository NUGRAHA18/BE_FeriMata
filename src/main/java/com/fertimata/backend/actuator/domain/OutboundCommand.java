package com.fertimata.backend.actuator.domain;

import java.time.Instant;
import java.util.Map;

/** What the transport needs, as a plain value not the JPA entity, since it runs outside the persistence transaction.
 * {@code expiresAt}: the device must discard the command after it; null when no command TTL is configured. */
public record OutboundCommand(
        String commandUid,
        String deviceCode,
        String actuatorCode,
        String commandType,
        Map<String, Object> parameters,
        Instant issuedAt,
        Instant expiresAt) {

    public OutboundCommand expiringAt(Instant expiry) {
        return new OutboundCommand(commandUid, deviceCode, actuatorCode, commandType, parameters, issuedAt, expiry);
    }
}
