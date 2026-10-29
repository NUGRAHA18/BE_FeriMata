package com.fertimata.backend.cultivation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TransplantDateRequest(@NotNull @Schema(example = "2026-09-12") LocalDate transplantDate) {}
