package com.fertimata.backend.calibration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Volume larutan yang dikeluarkan satu pompa dalam rentang waktu (Protokol §6 "volume tiap larutan").
 *
 * <p>Hanya perintah yang di-ACK EXECUTED yang dihitung. Durasi dipotong bila perintah berikutnya pada
 * pompa yang sama (mis. OFF) dieksekusi lebih dulu.
 */
@Schema(name = "DispensedVolume")
public record DispensedVolumeResponse(
        Long actuatorId,
        String actuatorCode,
        String actuatorName,
        Instant from,
        Instant to,
        @Schema(description = "Executed activating commands that started in the range") int runCount,
        @Schema(description = "Total run time, seconds") BigDecimal totalSeconds,
        @Schema(description = "Sum of run time x the flow calibrated before each run; null when no run was calibrated")
                BigDecimal volumeMl,
        @Schema(description = "Runs with no calibration before them; counted in totalSeconds but not in volumeMl")
                int uncalibratedRunCount,
        @Schema(description = "Runs whose duration is unknown (no duration parameter)") int unknownDurationRunCount,
        @Schema(description = "Flow of the current calibration, mL/s; null when never calibrated")
                BigDecimal currentFlowMlPerSecond,
        @Schema(description = "When the current calibration was made") Instant currentCalibratedAt) {}
