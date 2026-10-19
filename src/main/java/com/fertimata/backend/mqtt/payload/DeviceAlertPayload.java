package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of an alert the edge agent raises itself.
 *
 * <p>Detection rules that need the hardware in the loop - current through the pump while its relay is
 * off, a float switch that never changes, EC that does not converge after the maximum number of
 * dosing iterations - run on the edge. This message only reports the conclusion.
 *
 * @param type the device's own identifier, for example {@code SSR_SHORT}; free text
 * @param severity {@code INFO}, {@code WARNING} or {@code CRITICAL}; anything else is stored as WARNING
 * @param sensorCode optional code of a sensor on the same device the alert concerns
 * @param actuatorCode optional code of an actuator on the same device the alert concerns
 */
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
