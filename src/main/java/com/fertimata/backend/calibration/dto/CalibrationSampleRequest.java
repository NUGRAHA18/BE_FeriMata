package com.fertimata.backend.calibration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Satu sampel lab berpasangan.
 *
 * <p>{@code sensorValue} dan {@code covariates} yang dikosongkan diisi dari pesan telemetri terakhir
 * sensor ini pada {@code sampledAt} - itulah "ukur berpasangan dan simultan" di Protokol §7.
 */
@Schema(name = "CalibrationSampleRequest")
public record CalibrationSampleRequest(
        @Schema(description = "When the soil sample was taken; defaults to now") Instant sampledAt,
        @Schema(description = "Raw sensor value at that moment; defaults to the latest reading", example = "212")
                BigDecimal sensorValue,
        @NotNull @Schema(example = "0.18") BigDecimal labValue,
        @Size(max = 64) @Schema(example = "Kjeldahl") String labMethod,
        @Schema(description = "Covariate metricKey -> value; defaults to the other metrics of the same telemetry message")
                Map<@Size(max = 64) String, @NotNull BigDecimal> covariates,
        @Size(max = 64) @Schema(example = "LAB-2026-031") String sampleCode,
        @Size(max = 500) String notes) {}
