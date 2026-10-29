package com.fertimata.backend.alert;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Read model for an alert. Related entities are exposed as ids plus their business code. */
@Schema(name = "Alert")
public record AlertResponse(
        Long id,
        @Schema(example = "DEVICE_OFFLINE") String type,
        AlertSeverity severity,
        String title,
        String message,
        AlertSource source,
        Long relatedDeviceId,
        String relatedDeviceCode,
        Long relatedSensorId,
        String relatedSensorCode,
        Long relatedActuatorId,
        String relatedActuatorCode,
        boolean acknowledged,
        String acknowledgedBy,
        Instant acknowledgedAt,
        Instant createdAt,
        @JsonRawValue
                @Schema(
                        type = "object",
                        description = "Machine-readable details, e.g. failureCode (NO_RESPONSE, INTERLOCK_POWER, ...), commandUid; route on these, not on the message text")
                String metadata) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getType(),
                alert.getSeverity(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getSource(),
                alert.getRelatedDevice() == null ? null : alert.getRelatedDevice().getId(),
                alert.getRelatedDevice() == null ? null : alert.getRelatedDevice().getDeviceCode(),
                alert.getRelatedSensor() == null ? null : alert.getRelatedSensor().getId(),
                alert.getRelatedSensor() == null ? null : alert.getRelatedSensor().getCode(),
                alert.getRelatedActuator() == null ? null : alert.getRelatedActuator().getId(),
                alert.getRelatedActuator() == null ? null : alert.getRelatedActuator().getCode(),
                alert.isAcknowledged(),
                alert.getAcknowledgedBy() == null ? null : alert.getAcknowledgedBy().getUsername(),
                alert.getAcknowledgedAt(),
                alert.getCreatedAt(),
                alert.getMetadata());
    }
}
