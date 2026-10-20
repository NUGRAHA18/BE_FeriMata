package com.fertimata.backend.cultivation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Membuat siklus tanam. Tanpa {@code phases}, template fase bawaan dipakai. */
@Schema(name = "PlantingCycleRequest")
public record PlantingCycleRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") @Schema(example = "MT-2026-09") String code,
        @NotBlank @Size(max = 128) @Schema(example = "Musim tanam September 2026") String name,
        @Size(max = 128) @Schema(example = "Melon netted") String variety,
        @NotNull @Schema(example = "2026-09-12") LocalDate transplantDate,
        @Schema(example = "2026-11-20") LocalDate expectedHarvestDate,
        @Size(max = 1000) String notes,
        @Schema(description = "Start it as the active cycle (default true). Only one cycle may be active.")
                Boolean activate,
        List<@Valid GrowthPhaseRequest> phases) {}
