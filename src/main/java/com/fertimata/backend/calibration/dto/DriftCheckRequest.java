package com.fertimata.backend.calibration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "DriftCheckRequest")
public record DriftCheckRequest(
        @Schema(description = "Defaults to now") Instant checkedAt,
        @NotBlank @Size(max = 64) @Schema(example = "EC 1413 uS/cm") String standardName,
        @NotNull @Schema(example = "1413") BigDecimal referenceValue,
        @Schema(description = "Value read in the standard; defaults to the sensor's latest reading", example = "1388")
                BigDecimal measuredValue,
        @Schema(description = "Probe was cleaned before this check (Protokol §7 step 6)") Boolean probeCleaned,
        @Size(max = 500) String notes) {}
