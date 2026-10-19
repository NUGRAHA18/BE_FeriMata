package com.fertimata.backend.actuator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * A command the operator console asks the backend to deliver.
 *
 * <p>Only the command name and its parameters may be supplied. Everything else on the stored record
 * - who asked, when, from where, and what happened - is observed by the backend.
 *
 * <p>Parameters are validated for shape and size but not for meaning. {@code durationSeconds} is not
 * required and not range-checked here: safe dosing and run times are a hardware and agronomy
 * decision that has not been made, and enforcing an invented limit would be worse than enforcing
 * none. TODO(hardware): add per-command-type parameter rules once the contract is agreed.
 */
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
