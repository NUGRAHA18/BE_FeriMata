package com.fertimata.backend.calibration.dto;

import com.fertimata.backend.calibration.ActuatorCalibration;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "PumpCalibration")
public record PumpCalibrationResponse(
        Long id,
        Long actuatorId,
        String actuatorCode,
        Instant calibratedAt,
        BigDecimal flowMlPerSecond,
        int trialCount,
        @Schema(description = "[{durationSeconds, volumeMl}] when trials were recorded one by one") List<Object> trials,
        @Schema(description = "Coefficient of variation of the per-trial flow, %") BigDecimal flowCvPercent,
        BigDecimal busVoltage,
        String notes,
        String performedBy,
        @Schema(description = "True for the calibration used for new commands") boolean current,
        Instant createdAt) {

    public static PumpCalibrationResponse from(ActuatorCalibration c, List<Object> trials, boolean current) {
        return new PumpCalibrationResponse(
                c.getId(),
                c.getActuator().getId(),
                c.getActuator().getCode(),
                c.getCalibratedAt(),
                c.getFlowMlPerSecond(),
                c.getTrialCount(),
                trials,
                c.getFlowCvPercent(),
                c.getBusVoltage(),
                c.getNotes(),
                c.getPerformedBy() == null ? null : c.getPerformedBy().getUsername(),
                current,
                c.getCreatedAt());
    }
}
