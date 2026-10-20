package com.fertimata.backend.cultivation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "GrowthPhaseRequest")
public record GrowthPhaseRequest(
        @NotBlank @Size(max = 32) @Schema(example = "YOUNG_FRUIT") String code,
        @NotBlank @Size(max = 128) @Schema(example = "Buah muda") String name,
        @Min(0) @Schema(example = "25") int startHst,
        @Schema(description = "Inclusive; null = until the cycle ends", example = "44") Integer endHst,
        @Schema(example = "1.8") BigDecimal ecTargetMin,
        @Schema(example = "2.0") BigDecimal ecTargetMax,
        @Schema(example = "4.1") BigDecimal ratioN,
        @Schema(example = "1") BigDecimal ratioP,
        @Schema(example = "7.9") BigDecimal ratioK,
        @Size(max = 500) String notes) {}
