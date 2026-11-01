package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/** Alert concluded by the edge (hardware-in-loop rules like pump current with relay off, stuck float).
 * {@code severity}: INFO, WARNING or CRITICAL, anything else stored as WARNING; {@code type} is free text. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceAlertPayload(
        String type,
        String severity,
        String title,
        String message,
        String sensorCode,
        String actuatorCode,
        Instant timestamp,
        Map<String, Object> metadata) {}
