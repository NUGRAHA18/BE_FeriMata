package com.fertimata.backend.telemetry.dto;

import com.fertimata.backend.telemetry.domain.SensorReading;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model for a single measurement.
 *
 * <p>Both {@code value} and {@code textValue} are exposed: numeric metrics fill the first, and a
 * future non-numeric metric fills the second without a new endpoint.
 */
@Schema(name = "SensorReading")
public record SensorReadingResponse(
        Long id,
        Long sensorId,
        @Schema(example = "SENSOR-TEMP-01") String sensorCode,
        @Schema(example = "temperature") String metricKey,
        Long deviceId,
        @Schema(example = "JETSON-001") String deviceCode,
        @Schema(example = "28.4") BigDecimal value,
        @Schema(
                        example = "27.9",
                        description =
                                "Value corrected by the sensor's calibration valid at recordedAt; null when the sensor has no calibration or a covariate is missing. The raw value is never changed.")
                BigDecimal calibratedValue,
        String textValue,
        @Schema(example = "C") String unit,
        @Schema(description = "Timestamp reported by the device") Instant recordedAt,
        @Schema(description = "Timestamp the backend received the message") Instant receivedAt) {

    public static SensorReadingResponse from(SensorReading reading) {
        return from(reading, null);
    }

    public static SensorReadingResponse from(SensorReading reading, BigDecimal calibratedValue) {
        return new SensorReadingResponse(
                reading.getId(),
                reading.getSensor().getId(),
                reading.getSensor().getCode(),
                reading.getMetricKey(),
                reading.getDevice().getId(),
                reading.getDevice().getDeviceCode(),
                reading.getNumericValue(),
                calibratedValue,
                reading.getTextValue(),
                reading.getUnit(),
                reading.getRecordedAt(),
                reading.getReceivedAt());
    }
}
