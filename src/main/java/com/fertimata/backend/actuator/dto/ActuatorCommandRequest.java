package com.fertimata.backend.actuator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Operator command: only name and parameters are client-supplied; who/when/outcome is observed by the backend.
 * Parameters are checked for shape and size, not meaning. TODO(hardware): per-command-type rules. */
@Schema(
        name = "ActuatorCommandRequest",
        example = "{\"command\": \"ON\", \"parameters\": {\"durationSeconds\": 60}}")
public record ActuatorCommandRequest(
        @NotBlank
                @Size(max = 64)
                @Schema(description = "Command name understood by the device", example = "ON")
                String command,
        @Size(max = 32, message = "must not contain more than 32 parameters")
                @Schema(description = "Free-form arguments passed through to the device")
                Map<String, Object> parameters) {}
