package com.fertimata.backend.calibration.dto;

import com.fertimata.backend.calibration.CalibrationSample;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(name = "CalibrationSample")
public record CalibrationSampleResponse(
        Long id,
        Long sensorId,
        String sensorCode,
        Instant sampledAt,
        BigDecimal sensorValue,
        BigDecimal labValue,
        String labMethod,
        Map<String, Object> covariates,
        String sampleCode,
        String notes,
        String recordedBy,
        Instant createdAt) {

    public static CalibrationSampleResponse from(CalibrationSample s, Map<String, Object> covariates) {
        return new CalibrationSampleResponse(
                s.getId(),
                s.getSensor().getId(),
                s.getSensor().getCode(),
                s.getSampledAt(),
                s.getSensorValue(),
                s.getLabValue(),
                s.getLabMethod(),
                covariates,
                s.getSampleCode(),
                s.getNotes(),
                s.getRecordedBy() == null ? null : s.getRecordedBy().getUsername(),
                s.getCreatedAt());
    }
}
