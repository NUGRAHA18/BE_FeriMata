package com.fertimata.backend.sensor;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Operator edits (mainly naming/enabling auto-registered sensors); null = unchanged. Device, code, metric key are fixed. */
@Schema(name = "SensorUpdateRequest")
public record SensorUpdateRequest(
        @Size(max = 128) String name,
        @Size(max = 64) String type,
        @Size(max = 32) String unit,
        @Size(max = 255) String description,
        Boolean enabled,
        @Size(max = 32) @Schema(description = "Replaces the informational hardware binding") Map<String, Object> metadata) {}
