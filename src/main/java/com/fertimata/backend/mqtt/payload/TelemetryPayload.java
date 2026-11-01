package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/** Inbound telemetry (provisional); {@code data} keys are matched to registered sensors at runtime.
 * {@code deviceId} is advisory: the topic's device code wins, so a device cannot write as another. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryPayload(String deviceId, Instant timestamp, Map<String, Object> data) {}
