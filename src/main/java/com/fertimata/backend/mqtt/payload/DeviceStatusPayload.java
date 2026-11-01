package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/** Status/heartbeat; any message is proof of life. {@code actuators}: reported states, never assumed from commands.
 * {@code powerSource} (MAINS/BATTERY): Rev A derives it from PZEM-016 (low voltage or no answer = outage). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceStatusPayload(
        String deviceId,
        Instant timestamp,
        String status,
        Map<String, String> actuators,
        String powerSource,
        Map<String, Object> metadata) {}
