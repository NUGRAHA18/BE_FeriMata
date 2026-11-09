package com.fertimata.backend.irrigation.dto;

import com.fertimata.backend.actuator.domain.ActuatorCommand;
import com.fertimata.backend.actuator.domain.CommandStatus;
import com.fertimata.backend.irrigation.IrrigationRun;
import com.fertimata.backend.irrigation.IrrigationSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Schema(name = "IrrigationRun")
public record IrrigationRunResponse(
        Long id,
        @Schema(description = "Farm date (WIB)") LocalDate runDate,
        @Schema(description = "Watering time; null when the schedule was deleted") Long irrigationScheduleId,
        @Schema(example = "06:00", description = "Schedule time (WIB) at that run") String time,
        @Schema(
                        description = "STARTED (command sent to the pump); SKIPPED (refused by a safety rule or a "
                                + "fertilization in progress); FAILED (pump missing or command not sent); MISSED (backend "
                                + "was down); WAITING (waiting for fertilization to finish)")
                IrrigationSchedule.RunResult result,
        @Schema(
                        example = "FLOAT_LOW",
                        description = "BACKEND_DOWN, FERTILIZING, FERTILIZATION_IN_PROGRESS, PUMP_UNAVAILABLE, an interlock "
                                + "code (BACKUP_POWER, FLOAT_LOW, ...) or an API error code. Translate this, not message.")
                String reasonCode,
        @Schema(description = "Technical detail") String message,
        @Schema(description = "Seconds sent to the pump") Integer durationSeconds,
        String commandUid,
        @Schema(description = "Status of that command on the device (EXECUTED, FAILED, ...); null without a command")
                CommandStatus commandStatus,
        @Schema(description = "Device failure code when the command failed") String commandFailureCode,
        @Schema(description = "Device or transport error when the command failed") String commandError,
        Instant recordedAt) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static IrrigationRunResponse from(IrrigationRun r, ActuatorCommand command) {
        return new IrrigationRunResponse(
                r.getId(), r.getRunDate(), r.getIrrigationScheduleId(), HH_MM.format(r.getScheduledTime()), r.getResult(),
                r.getReasonCode(), r.getMessage(), r.getDurationSeconds(), r.getCommandUid(),
                command == null ? null : command.getStatus(),
                command == null ? null : command.getFailureCode(),
                command == null ? null : command.getErrorMessage(),
                r.getRecordedAt());
    }
}
