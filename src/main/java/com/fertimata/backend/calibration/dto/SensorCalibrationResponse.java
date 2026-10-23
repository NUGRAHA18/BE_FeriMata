package com.fertimata.backend.calibration.dto;

import com.fertimata.backend.calibration.CalibrationMethod;
import com.fertimata.backend.calibration.SensorCalibration;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(name = "SensorCalibration")
public record SensorCalibrationResponse(
        @Schema(description = "Null for an unsaved fit preview") Long id,
        Long sensorId,
        String sensorCode,
        String metricKey,
        CalibrationMethod method,
        BigDecimal intercept,
        BigDecimal slope,
        @Schema(description = "Covariate metricKey -> coefficient") Map<String, Object> covariates,
        Instant validFrom,
        Integer sampleCount,
        BigDecimal rSquared,
        BigDecimal rmse,
        BigDecimal mae,
        @Schema(description = "Bland-Altman mean difference (calibrated - lab)") BigDecimal bias,
        @Schema(description = "Bland-Altman lower limit of agreement, bias - 1.96 SD") BigDecimal loaLower,
        @Schema(description = "Bland-Altman upper limit of agreement, bias + 1.96 SD") BigDecimal loaUpper,
        String notes,
        String createdBy,
        @Schema(description = "True for the calibration applied to new readings") boolean current,
        Instant createdAt) {

    public static SensorCalibrationResponse from(SensorCalibration c, Map<String, Object> covariates, boolean current) {
        return new SensorCalibrationResponse(
                c.getId(),
                c.getSensor().getId(),
                c.getSensor().getCode(),
                c.getSensor().getMetricKey(),
                c.getMethod(),
                c.getIntercept(),
                c.getSlope(),
                covariates,
                c.getValidFrom(),
                c.getSampleCount(),
                c.getRSquared(),
                c.getRmse(),
                c.getMae(),
                c.getBias(),
                c.getLoaLower(),
                c.getLoaUpper(),
                c.getNotes(),
                c.getCreatedBy() == null ? null : c.getCreatedBy().getUsername(),
                current,
                c.getCreatedAt());
    }
}
