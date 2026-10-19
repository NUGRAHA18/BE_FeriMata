package com.fertimata.backend.sensor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Operator-supplied registration for a measurement channel. */
@Schema(name = "SensorRegistrationRequest")
public record SensorRegistrationRequest(
        @NotNull Long deviceId,
        @NotBlank @Size(max = 64) @Schema(example = "SOIL-A-PH") String code,
        @NotBlank
                @Size(max = 64)
                @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may only contain letters, digits, dot, underscore and hyphen")
                @Schema(example = "soil_a.ph", description = "Namespaced with a dot when one device has several instruments of the same kind")
                String metricKey,
        @NotBlank @Size(max = 128) @Schema(example = "Canopy temperature") String name,
        @Size(max = 64) @Schema(example = "TEMPERATURE") String type,
        @Size(max = 32) @Schema(example = "C") String unit,
        @Size(max = 255) String description,
        @Size(max = 32) @Schema(description = "Informational hardware binding, e.g. Modbus address and location")
                Map<String, Object> metadata) {}
