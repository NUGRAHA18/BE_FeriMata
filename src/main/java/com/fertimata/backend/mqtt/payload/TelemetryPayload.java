package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of an inbound telemetry message.
 *
 * <p><b>Provisional.</b> The example the hardware team sketched is:
 *
 * <pre>{@code
 * { "deviceId": "JETSON-001",
 *   "timestamp": "2026-09-06T12:00:00Z",
 *   "data": { "temperature": 28.4, "ph": 6.2, "ec": 1.8, "tds": 920 } }
 * }</pre>
 *
 * <p>The metric names in {@code data} are not fixed anywhere in this codebase - they are looked up
 * against registered sensors at runtime. Unknown keys are handled by policy, not by failing.
 *
 * @param deviceId advisory only; the device code from the topic is authoritative, because a device
 *     must not be able to write telemetry attributed to another device
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryPayload(String deviceId, Instant timestamp, Map<String, Object> data) {}
