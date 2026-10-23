package com.fertimata.backend.plant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Satu buah yang dipanen. Semua ukuran opsional. */
@Schema(name = "HarvestRequest")
public record HarvestRequest(
        @Schema(description = "Defaults to today (Asia/Jakarta)") LocalDate harvestedOn,
        @DecimalMin("0") @Schema(example = "1450") BigDecimal fruitWeightG,
        @DecimalMin("0") @DecimalMax("40") @Schema(example = "13.5", description = "°Brix from the refractometer") BigDecimal brix,
        @DecimalMin("0") @Schema(example = "32.5") BigDecimal firmness,
        @Size(max = 16) @Schema(example = "N") String firmnessUnit,
        @DecimalMin("0") @Schema(example = "34.0") BigDecimal fleshThicknessMm,
        @DecimalMin("0") @DecimalMax("100") @Schema(example = "11.8") BigDecimal dryMatterPercent,
        @Size(max = 1000) String notes) {}
