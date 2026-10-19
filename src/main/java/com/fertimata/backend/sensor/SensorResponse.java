package com.fertimata.backend.sensor;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Read model for a sensor. */
@Schema(name = "Sensor")
public record SensorResponse(
        Long id,
        Long deviceId,
        @Schema(example = "JETSON-001") String deviceCode,
        @Schema(example = "SENSOR-TEMP-01") String code,
        @Schema(description = "Key this sensor uses inside a telemetry payload", example = "temperature")
                String metricKey,
        String name,
        @Schema(example = "TEMPERATURE") String type,
        @Schema(example = "C") String unit,
        boolean enabled,
        @Schema(description = "Created automatically from an unrecognised telemetry metric") boolean autoRegistered,
        String description,
        @JsonRawValue @Schema(type = "object", description = "Informational hardware binding; never used for decisions")
                String metadata,
        Instant createdAt,
        Instant updatedAt) {

    public static SensorResponse from(Sensor sensor) {
        return new SensorResponse(
                sensor.getId(),
                sensor.getDevice().getId(),
                sensor.getDevice().getDeviceCode(),
                sensor.getCode(),
                sensor.getMetricKey(),
                sensor.getName(),
                sensor.getType(),
                sensor.getUnit(),
                sensor.isEnabled(),
                sensor.isAutoRegistered(),
                sensor.getDescription(),
                sensor.getMetadata(),
                sensor.getCreatedAt(),
                sensor.getUpdatedAt());
    }
}
