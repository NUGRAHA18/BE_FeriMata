package com.fertimata.backend.calibration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Model koreksi yang dihitung di luar (mis. R atau SPSS) dan dimasukkan langsung.
 *
 * <p>Statistik kesesuaian yang dikosongkan dihitung dari sampel lab sensor ini bila ada.
 */
@Schema(name = "SensorCalibrationRequest")
public record SensorCalibrationRequest(
        @NotNull @Schema(example = "3.2") BigDecimal intercept,
        @NotNull @Schema(example = "0.84") BigDecimal slope,
        @Schema(description = "Covariate metricKey -> coefficient; empty for a plain linear model")
                Map<@Size(max = 64) String, @NotNull BigDecimal> covariates,
        @Schema(description = "Readings recorded from this instant on use the model; defaults to now") Instant validFrom,
        @Min(1) Integer sampleCount,
        BigDecimal rSquared,
        BigDecimal rmse,
        BigDecimal mae,
        @Size(max = 500) String notes) {}
