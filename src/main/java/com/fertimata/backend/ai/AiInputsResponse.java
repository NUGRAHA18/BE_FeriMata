package com.fertimata.backend.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Snapshot sensor dengan nama field form layanan AI ({@code run_fertilization}/{@code run_irrigation}); hanya dipakai
 * bila {@code readyFor…} true, field kosong/basi bernilai null dan disebut di {@code problems}. */
@Schema(name = "AiInputs")
public record AiInputsResponse(
        Instant generatedAt,
        @Schema(description = "Readings older than this are stale", example = "900") long maxAgeSeconds,
        @Schema(description = "Days since the sowing date; null when it is not set in Settings") Integer hst,
        @Schema(example = "PEMBUAHAN") String phaseCode,
        @Schema(example = "Pembuahan") String phaseName,
        @Schema(description = "All fertilization fields present, fresh, and HST known") boolean readyForFertilization,
        @Schema(description = "All irrigation fields present, fresh, and HST known") boolean readyForIrrigation,
        @Schema(description = "Form fields for run_fertilization: ec, ph, nitrogen_tanah, phosfor_tanah, kalium_tanah, hst")
                Map<String, BigDecimal> fertilization,
        @Schema(description = "Form fields for run_irrigation: ec_air, suhu_air, suhu_udara, rh, humidity_tanah, temp_tanah, ph, nitrogen, phosfor, kalium, ec_tanah, hst")
                Map<String, BigDecimal> irrigation,
        @Schema(description = "Per metric key: the reading behind each field") Map<String, Reading> readings,
        @Schema(description = "Why a form is not ready, e.g. \"fertilization.ph (tank.ph): stale, 1820 s old\"")
                List<String> problems) {

    @Schema(name = "AiInputReading")
    public record Reading(
            @Schema(description = "Calibrated value when the sensor has a calibration, else raw") BigDecimal value,
            BigDecimal rawValue,
            boolean calibrated,
            String unit,
            Instant recordedAt,
            long ageSeconds,
            boolean fresh) {}
}
