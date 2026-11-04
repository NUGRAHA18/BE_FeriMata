package com.fertimata.backend.fertilization.dto;

import com.fertimata.backend.fertilization.FertilizationDoseSetting;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "FertilizationDose")
public record FertilizationDoseResponse(
        @Schema(description = "AI (default) or MANUAL") FertilizationDoseSetting.Mode mode,
        @Schema(description = "Farmer's millilitres; kept while mode is AI so they need not be typed again") BigDecimal nitrogenMl,
        BigDecimal phosphorusMl,
        BigDecimal potassiumMl,
        Instant updatedAt,
        String updatedBy,
        @Schema(description = "Dosing pumps N, P, K: what a manual dose needs") List<Pump> pumps) {

    @Schema(name = "FertilizationDosePump")
    public record Pump(
            @Schema(example = "N") String nutrient,
            @Schema(example = "DOSING-N") String actuatorCode,
            @Schema(description = "False when the pump is missing or disabled") boolean available,
            @Schema(description = "Latest flow calibration; null = not calibrated, so a manual dose cannot be used")
                    BigDecimal flowMlPerSecond,
            @Schema(description = "Most millilitres per run = maxRunSeconds x flow; null when unknown") BigDecimal maxMl) {}
}
