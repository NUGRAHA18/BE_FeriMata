package com.fertimata.backend.cultivation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Tanggal semai = HST hari ke-0 (keputusan 31 Okt 2026). {@code transplantDate} diterima untuk frontend lama. */
public record SowingDateRequest(
        @NotNull @JsonAlias("transplantDate") @Schema(example = "2026-09-12", description = "Sowing date; HST day 0")
                LocalDate sowingDate) {}
