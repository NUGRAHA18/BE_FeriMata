package com.fertimata.backend.sensor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Operator edits to a sensor.
 *
 * <p>Exists mainly so an auto-registered sensor can be named and switched on after review. Null
 * fields are left unchanged; the device, code and metric key are not editable because history rows
 * already reference them.
 */
@Schema(name = "SensorUpdateRequest")
public record SensorUpdateRequest(
        @Size(max = 128) String name,
        @Size(max = 64) String type,
        @Size(max = 32) String unit,
        @Size(max = 255) String description,
        Boolean enabled,
        @Size(max = 32) @Schema(description = "Replaces the informational hardware binding") Map<String, Object> metadata) {}
