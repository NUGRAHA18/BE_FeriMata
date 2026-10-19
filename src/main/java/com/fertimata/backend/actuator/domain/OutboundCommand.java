package com.fertimata.backend.actuator.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Everything the transport needs to deliver a command, and nothing more.
 *
 * <p>A plain value object rather than the JPA entity: the transport runs outside the persistence
 * transaction, so handing it a managed entity would either force lazy associations to be loaded at
 * the wrong time or fail outright. It also keeps the messaging layer from reaching into the domain
 * model for fields it has no business reading.
 *
 * @param expiresAt after this instant the device must discard the command instead of executing it;
 *     null when no command TTL is configured
 */
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
