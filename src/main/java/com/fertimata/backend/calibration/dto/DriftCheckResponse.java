package com.fertimata.backend.calibration.dto;

import com.fertimata.backend.calibration.SensorDriftCheck;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "DriftCheck")
public record DriftCheckResponse(
        Long id,
        Long sensorId,
        String sensorCode,
        Instant checkedAt,
        String standardName,
        BigDecimal referenceValue,
        BigDecimal measuredValue,
        @Schema(description = "measured - reference") BigDecimal deviation,
        @Schema(description = "deviation / reference x 100; null when the reference is 0") BigDecimal deviationPercent,
        boolean probeCleaned,
        String notes,
        String performedBy,
        Instant createdAt) {

    public static DriftCheckResponse from(SensorDriftCheck d) {
        return new DriftCheckResponse(
                d.getId(),
                d.getSensor().getId(),
                d.getSensor().getCode(),
                d.getCheckedAt(),
                d.getStandardName(),
                d.getReferenceValue(),
                d.getMeasuredValue(),
                d.getDeviation(),
                d.getDeviationPercent(),
                d.isProbeCleaned(),
                d.getNotes(),
                d.getPerformedBy() == null ? null : d.getPerformedBy().getUsername(),
                d.getCreatedAt());
    }
}
