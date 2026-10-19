package com.fertimata.backend.actuator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Operator-supplied registration for a controllable output. */
@Schema(name = "ActuatorRegistrationRequest")
public record ActuatorRegistrationRequest(
        @NotNull Long deviceId,
        @NotBlank @Size(max = 64) @Schema(example = "DOSING-N") String code,
        @NotBlank @Size(max = 128) @Schema(example = "Pompa dosing N") String name,
        @Size(max = 64) @Schema(example = "DOSING_PUMP") String type,
        @Size(max = 255) String description,
        @Positive @Schema(description = "Longest run one activating command may request; omit for no limit")
                Integer maxRunSeconds,
        @Size(max = 32) @Schema(description = "Informational hardware binding, e.g. Modbus address and channel")
                Map<String, Object> metadata) {}
