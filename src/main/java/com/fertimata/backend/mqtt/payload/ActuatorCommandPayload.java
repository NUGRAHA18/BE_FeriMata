package com.fertimata.backend.mqtt.payload;

import java.time.Instant;
import java.util.Map;

/** Wire command: names an actuator and an intent, no GPIO/driver hints. The device must discard it after
 * {@code expiresAt} (e.g. delivered late after reconnect); null when no TTL is configured. */
public record ActuatorCommandPayload(
        String commandUid,
        String deviceId,
        String actuatorCode,
        String command,
        Map<String, Object> parameters,
        Instant issuedAt,
        Instant expiresAt) {}
