package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of a device status / heartbeat message.
 *
 * <p>Any message on the status topic counts as proof of life, even one that carries nothing else.
 *
 * @param status optional self-reported state; absence simply means "still here"
 * @param actuators optional map of actuator code to reported state, which is how the backend learns
 *     what the hardware is actually doing rather than assuming a command took effect
 * @param powerSource optional site power source as the edge agent judges it, for example
 *     {@code MAINS} or {@code BATTERY}. On FERTIMATA Rev A the agent derives it from the PZEM-016
 *     reading (low mains voltage, or the meter not answering, means an outage)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceStatusPayload(
        String deviceId,
        Instant timestamp,
        String status,
        Map<String, String> actuators,
        String powerSource,
        Map<String, Object> metadata) {}
