package com.fertimata.backend.fertilization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "FertilizationSchedule")
public record FertilizationScheduleResponse(
        @Schema(description = "app.fertilization.enabled: false = watering is never preceded by fertilization") boolean enabled,
        @Schema(description = "AI_SERVICE_URL is set") boolean aiConfigured,
        @Schema(description = "The AI schedule could be read; when false intervalDays is unknown and aiError says why")
                boolean aiReachable,
        String aiError,
        @Schema(description = "Row id in the AI service's dosing_schedules") Long aiScheduleId,
        @Schema(description = "Fertilize every N days (from the AI service); null = no fertilization schedule", example = "7")
                Integer intervalDays,
        @Schema(description = "Farm date of the last DONE/PARTIAL run; null = never") LocalDate lastFertilizedDate,
        Instant lastFertilizedAt,
        @Schema(
                        description = "Fertilization happens before the first watering session on or after this date; "
                                + "null when there is no AI schedule")
                LocalDate nextDueDate,
        @Schema(description = "The run in progress, if any") FertilizationRunResponse activeRun) {}
