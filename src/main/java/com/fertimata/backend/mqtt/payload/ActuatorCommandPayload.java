package com.fertimata.backend.mqtt.payload;

import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of a command the backend publishes to a device.
 *
 * <p>Note what is absent: no GPIO pin, no serial port, no driver hint. The backend names an actuator
 * and an intent; translating that into hardware is the device's job.
 *
 * @param expiresAt the device must discard the command after this instant instead of executing it
 *     late, for example after a reconnect delivers a queued message; null when no TTL is configured
 */
public record ActuatorCommandPayload(
        String commandUid,
        String deviceId,
        String actuatorCode,
        String command,
        Map<String, Object> parameters,
        Instant issuedAt,
        Instant expiresAt) {}
