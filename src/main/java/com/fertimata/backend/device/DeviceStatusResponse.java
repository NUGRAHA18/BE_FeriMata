package com.fertimata.backend.device;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Liveness view; includes the timeout so the PWA does not hard-code it. */
@Schema(name = "DeviceStatus")
public record DeviceStatusResponse(
        Long id,
        String deviceCode,
        DeviceStatus status,
        Instant lastSeenAt,
        Long secondsSinceLastSeen,
        long offlineTimeoutSeconds,
        @Schema(description = "Last power source the device reported; null if it never reports one", example = "MAINS")
                String powerSource,
        Instant powerSourceUpdatedAt,
        @Schema(description = "True when the reported power source is not a configured normal supply")
                boolean onBackupPower) {}
