package com.fertimata.backend.actuator.dto;

import com.fertimata.backend.actuator.domain.ActuatorCommand;
import com.fertimata.backend.actuator.domain.CommandSource;
import com.fertimata.backend.actuator.domain.CommandStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/** Audit view of one command attempt. */
@Schema(name = "ActuatorCommand")
public record ActuatorCommandResponse(
        Long id,
        @Schema(description = "Correlation id echoed by the device in its acknowledgement") String commandUid,
        Long actuatorId,
        String actuatorCode,
        Long deviceId,
        String deviceCode,
        @Schema(example = "ON") String commandType,
        Map<String, Object> parameters,
        CommandSource source,
        @Schema(description = "Username that requested it; null for automation and system commands")
                String requestedBy,
        CommandStatus status,
        Instant requestedAt,
        Instant sentAt,
        Instant executedAt,
        String errorMessage) {

    public static ActuatorCommandResponse from(ActuatorCommand command, Map<String, Object> parameters) {
        return new ActuatorCommandResponse(
                command.getId(),
                command.getCommandUid(),
                command.getActuator().getId(),
                command.getActuator().getCode(),
                command.getDevice().getId(),
                command.getDevice().getDeviceCode(),
                command.getCommandType(),
                parameters,
                command.getSource(),
                command.getRequestedBy() == null ? null : command.getRequestedBy().getUsername(),
                command.getStatus(),
                command.getRequestedAt(),
                command.getSentAt(),
                command.getExecutedAt(),
                command.getErrorMessage());
    }
}
