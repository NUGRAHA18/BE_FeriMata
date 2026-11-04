package com.fertimata.backend.fertilization.dto;

import com.fertimata.backend.fertilization.FertilizationDoseSetting;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(name = "FertilizationDoseRequest")
public record FertilizationDoseRequest(
        @NotNull
                @Schema(
                        description = "AI = dose from the AI fuzzy fertilization (default); MANUAL = the millilitres below",
                        example = "MANUAL")
                FertilizationDoseSetting.Mode mode,
        @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
                @Schema(description = "Millilitres of nutrient N per fertilization; required for MANUAL", example = "50")
                BigDecimal nitrogenMl,
        @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
                @Schema(description = "Millilitres of nutrient P; required for MANUAL", example = "30")
                BigDecimal phosphorusMl,
        @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
                @Schema(description = "Millilitres of nutrient K; required for MANUAL", example = "40")
                BigDecimal potassiumMl) {}
