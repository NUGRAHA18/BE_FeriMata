package com.fertimata.backend.calibration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Kalibrasi debit pompa (Protokol §8). Isi {@code trials} (dianjurkan; debit dihitung dari total
 * volume / total durasi) atau langsung {@code flowMlPerSecond} + {@code trialCount}.
 */
@Schema(name = "PumpCalibrationRequest")
public record PumpCalibrationRequest(
        @Schema(description = "Defaults to now") Instant calibratedAt,
        @Size(max = 20) List<@Valid @NotNull Trial> trials,
        @DecimalMin(value = "0", inclusive = false) @Schema(example = "2.05") BigDecimal flowMlPerSecond,
        @Min(1) Integer trialCount,
        @Schema(description = "Bus voltage during the test; defaults to the latest mains voltage reading", example = "221.4")
                BigDecimal busVoltage,
        @Size(max = 500) String notes) {

    @Schema(name = "PumpCalibrationTrial")
    public record Trial(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Schema(example = "30") BigDecimal durationSeconds,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Schema(example = "61.5") BigDecimal volumeMl) {}
}
