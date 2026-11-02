package com.fertimata.backend.fertilization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "FertilizationIntervalRequest")
public record FertilizationIntervalRequest(
        @NotNull @Min(1) @Max(60) @Schema(description = "Fertilize every N days", example = "7") Integer intervalDays) {}
