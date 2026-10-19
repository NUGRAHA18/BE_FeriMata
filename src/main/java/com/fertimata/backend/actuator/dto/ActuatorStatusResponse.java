package com.fertimata.backend.actuator.dto;

import com.fertimata.backend.actuator.domain.Actuator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Current state of an actuator as last reported by its device.
 *
 * <p>{@code currentState} is null until the device reports one. The backend never guesses a state
 * from the commands it sent.
 */
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
