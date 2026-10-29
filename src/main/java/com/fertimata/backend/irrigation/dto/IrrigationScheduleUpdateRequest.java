package com.fertimata.backend.irrigation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/** Semua field opsional; yang kosong tidak diubah. */
public record IrrigationScheduleUpdateRequest(
        @Schema(type = "string", example = "17:30") LocalTime time,
        @Min(1) Integer durationSeconds,
        Boolean enabled,
        @Size(max = 255) String notes) {}
