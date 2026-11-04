package com.fertimata.backend.fertilization.dto;

import com.fertimata.backend.fertilization.FertilizationDoseSetting;
import com.fertimata.backend.fertilization.FertilizationRun;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "FertilizationRun")
public record FertilizationRunResponse(
        Long id,
        @Schema(description = "Farm date (WIB)") LocalDate runDate,
        @Schema(description = "Watering time that waited for this run; null when deleted") Long irrigationScheduleId,
        @Schema(
                        description = "IN_PROGRESS; DONE (dose poured, also when the AI said 0 s); PARTIAL (some pumps "
                                + "ran, counted as fertilized so it is not doubled); SKIPPED (not poured: stale sensor data, "
                                + "pump missing); FAILED (not poured: AI unusable, command refused, backend down)")
                FertilizationRun.Status status,
        @Schema(description = "Next step while IN_PROGRESS: PHOSPHORUS, POTASSIUM, MIXING, IRRIGATE") FertilizationRun.Step step,
        @Schema(
                        example = "AI_INPUTS_NOT_READY",
                        description = "AI_UNAVAILABLE, AI_INPUTS_NOT_READY, AI_NO_PHOTO, PUMP_UNAVAILABLE, AI_NO_DOSE_NEEDED, AI_CAPPED, "
                                + "TANK_DATA_NOT_READY, PUMP_NOT_CALIBRATED, DOSE_CAPPED (manual dose), "
                                + "BACKEND_DOWN, or an interlock code (BACKUP_POWER, FLOAT_LOW, ...). Translate this, not message.")
                String reasonCode,
        @Schema(description = "Technical detail") String message,
        @Schema(description = "AI dosing interval at the time of the run") Integer intervalDays,
        @Schema(description = "Seconds asked by the AI fuzzy fertilization") BigDecimal aiNitrogenSeconds,
        BigDecimal aiPhosphorusSeconds,
        BigDecimal aiPotassiumSeconds,
        @Schema(description = "AI (fuzzy fertilization) or MANUAL (farmer's ml); null when the dose was never decided")
                FertilizationDoseSetting.Mode doseSource,
        @Schema(description = "Farmer's millilitres when doseSource is MANUAL") BigDecimal manualNitrogenMl,
        BigDecimal manualPhosphorusMl,
        BigDecimal manualPotassiumMl,
        @Schema(description = "Seconds sent to the pump (AI seconds, or ml / calibrated flow; capped to maxRunSeconds)")
                Integer nitrogenSeconds,
        Integer phosphorusSeconds,
        Integer potassiumSeconds,
        Integer stirrerSeconds,
        String stirrerCommandUid,
        String nitrogenCommandUid,
        String phosphorusCommandUid,
        String potassiumCommandUid,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {

    public static FertilizationRunResponse from(FertilizationRun r) {
        return new FertilizationRunResponse(
                r.getId(), r.getRunDate(), r.getIrrigationScheduleId(), r.getStatus(), r.getStep(), r.getReasonCode(),
                r.getMessage(), r.getIntervalDays(), r.getAiNitrogenSeconds(), r.getAiPhosphorusSeconds(),
                r.getAiPotassiumSeconds(), r.getDoseSource(), r.getManualNitrogenMl(), r.getManualPhosphorusMl(),
                r.getManualPotassiumMl(), r.getNitrogenSeconds(), r.getPhosphorusSeconds(), r.getPotassiumSeconds(),
                r.getStirrerSeconds(), r.getStirrerCommandUid(), r.getNitrogenCommandUid(), r.getPhosphorusCommandUid(),
                r.getPotassiumCommandUid(), r.getStartedAt(), r.getFinishedAt(), r.getCreatedAt());
    }
}
