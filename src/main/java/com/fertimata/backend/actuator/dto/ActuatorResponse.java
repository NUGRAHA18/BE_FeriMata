package com.fertimata.backend.actuator.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fertimata.backend.actuator.domain.Actuator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "Actuator")
public record ActuatorResponse(
        Long id,
        Long deviceId,
        @Schema(example = "RIO-TANK-01") String deviceCode,
        @Schema(example = "DOSING-N") String code,
        String name,
        @Schema(description = "Free text; the hardware catalogue decides the values", example = "DOSING_PUMP")
                String type,
        boolean enabled,
        @Schema(description = "Last state reported by the device", example = "OFF") String currentState,
        Instant stateUpdatedAt,
        @Schema(description = "Longest run one activating command may request; null means no limit")
                Integer maxRunSeconds,
        @Schema(
                        description = "Command names this actuator accepts; null when its type is unrestricted",
                        example = "[\"ON\", \"OFF\"]")
                List<String> supportedCommands,
        @Schema(
                        description = "True when this actuator's type takes a pump flow calibration"
                                + " (app.calibration.pump.types); the calibration endpoints reject the others")
                boolean pumpCalibratable,
        String description,
        @JsonRawValue
                @Schema(type = "object", description = "Informational hardware binding; never used for decisions")
                String metadata,
        Instant createdAt,
        Instant updatedAt) {

    public static ActuatorResponse from(Actuator actuator, List<String> supportedCommands, boolean pumpCalibratable) {
        return new ActuatorResponse(
                actuator.getId(),
                actuator.getDevice().getId(),
                actuator.getDevice().getDeviceCode(),
                actuator.getCode(),
                actuator.getName(),
                actuator.getType(),
                actuator.isEnabled(),
                actuator.getCurrentState(),
                actuator.getStateUpdatedAt(),
                actuator.getMaxRunSeconds(),
                supportedCommands,
                pumpCalibratable,
                actuator.getDescription(),
                actuator.getMetadata(),
                actuator.getCreatedAt(),
                actuator.getUpdatedAt());
    }
}
