package com.fertimata.backend.actuator.dto;

import com.fertimata.backend.actuator.domain.Actuator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Last device-reported state; {@code currentState} is null until reported, never guessed from sent commands. */
@Schema(name = "ActuatorStatus")
public record ActuatorStatusResponse(
        Long id, String code, boolean enabled, String currentState, Instant stateUpdatedAt, String deviceCode) {

    public static ActuatorStatusResponse from(Actuator actuator) {
        return new ActuatorStatusResponse(
                actuator.getId(),
                actuator.getCode(),
                actuator.isEnabled(),
                actuator.getCurrentState(),
                actuator.getStateUpdatedAt(),
                actuator.getDevice().getDeviceCode());
    }
}
