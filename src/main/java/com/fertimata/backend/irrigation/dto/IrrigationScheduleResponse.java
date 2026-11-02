package com.fertimata.backend.irrigation.dto;

import com.fertimata.backend.irrigation.IrrigationSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "IrrigationSchedule")
public record IrrigationScheduleResponse(
        Long id,
        @Schema(example = "06:00", description = "Farm time (WIB)") String time,
        @Schema(
                        example = "300",
                        description = "Fallback duration: used when the AI fuzzy irrigation cannot be used (or always when "
                                + "app.irrigation.duration-source=MANUAL). Normally the AI decides the duration at run time.")
                int durationSeconds,
        boolean enabled,
        String notes,
        @Schema(description = "Next start; null when disabled or irrigation scheduling is switched off") Instant nextRunAt,
        Instant lastRunAt,
        @Schema(description = "STARTED, SKIPPED (refused by a safety rule), FAILED, MISSED (backend was down)")
                IrrigationSchedule.RunResult lastResult,
        @Schema(
                        example = "BACKUP_POWER",
                        description = "Why it was skipped/failed/missed: an interlock code (BACKUP_POWER, FLOAT_LOW, "
                                + "EXCLUSIVE_OPERATION, BLOCKED_WHILE_ACTIVE, DEVICE_NOT_ONLINE), an API error code, "
                                + "PUMP_UNAVAILABLE or BACKEND_DOWN. Translate this, not lastMessage.")
                String lastReasonCode,
        @Schema(description = "Technical detail; English for safety refusals") String lastMessage,
        @Schema(description = "Command of the last run; GET /api/actuators/commands/{commandUid}") String lastCommandUid,
        @Schema(description = "Seconds actually sent to the pump on the last run; null when it did not get that far")
                Integer lastDurationSeconds,
        @Schema(
                        example = "AI",
                        description = "AI (from the AI fuzzy irrigation), FALLBACK (AI unusable; lastReasonCode says why: "
                                + "AI_NOT_CONFIGURED, AI_INPUTS_NOT_READY, AI_NO_PHOTO, AI_UNAVAILABLE) or MANUAL")
                String lastDurationSource) {}
