package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Wire shape of a device acknowledgement for a command it was given.
 *
 * <p>This is the only thing that can move a command to EXECUTED or FAILED. Publishing to the broker
 * proves nothing about the hardware.
 *
 * @param commandUid correlation id copied from the command the backend published
 * @param success whether the device carried the command out
 * @param actuatorState state the actuator ended up in, if the device reports it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandAckPayload(
        String commandUid, Boolean success, String actuatorState, Instant executedAt, String errorMessage) {}
