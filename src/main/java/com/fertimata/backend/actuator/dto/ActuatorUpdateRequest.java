package com.fertimata.backend.actuator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Operator edits to an actuator.
 *
 * <p>Null fields are left unchanged. The device and code are not editable because command history
 * references them. Disabling is the quick way to take a suspect output out of service - for example
 * the distribution pump while its SSR is suspected of failing short.
 */
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
