package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** Device ACK, the only thing that moves a command to EXECUTED or FAILED; {@code commandUid} is the
 * correlation id from the published command. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandAckPayload(
        String commandUid, Boolean success, String actuatorState, Instant executedAt, String errorMessage) {}
