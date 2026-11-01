package com.fertimata.backend.actuator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Operator edits; null fields stay unchanged. Device and code are immutable because command history references them.
 * Disabling takes a suspect output (e.g. distribution pump with a failing SSR) out of service. */
@Schema(name = "ActuatorUpdateRequest")
public record ActuatorUpdateRequest(
        @Size(max = 128) String name,
        @Size(max = 64) String type,
        @Size(max = 255) String description,
        Boolean enabled,
        @PositiveOrZero
                @Schema(description = "New run-time limit in seconds; 0 removes the limit")
                Integer maxRunSeconds,
        @Size(max = 32) @Schema(description = "Replaces the informational hardware binding")
                Map<String, Object> metadata) {}
