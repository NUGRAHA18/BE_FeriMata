package com.fertimata.backend.irrigation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record IrrigationScheduleRequest(
        @NotNull @Schema(type = "string", example = "06:00", description = "Farm time (WIB), HH:mm") LocalTime time,
        @NotNull @Min(1) @Schema(example = "300", description = "Seconds; at most the pump's maxRunSeconds") Integer durationSeconds,
        @Schema(description = "Default true") Boolean enabled,
        @Size(max = 255) String notes) {}
