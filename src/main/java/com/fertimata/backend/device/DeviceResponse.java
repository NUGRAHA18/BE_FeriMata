package com.fertimata.backend.device;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Read model for a device. */
@Schema(name = "Device")
public record DeviceResponse(
        Long id,
        @Schema(example = "JETSON-01") String deviceCode,
        @Schema(example = "Jetson Orin Nano") String name,
        @Schema(example = "EDGE_GATEWAY") String type,
        DeviceStatus status,
        Instant lastSeenAt,
        @Schema(description = "Last power source the device reported", example = "MAINS") String powerSource,
        Instant powerSourceUpdatedAt,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getDeviceCode(),
                device.getName(),
                device.getType(),
                device.getStatus(),
                device.getLastSeenAt(),
                device.getPowerSource(),
                device.getPowerSourceUpdatedAt(),
                device.getDescription(),
                device.getCreatedAt(),
                device.getUpdatedAt());
    }
}
