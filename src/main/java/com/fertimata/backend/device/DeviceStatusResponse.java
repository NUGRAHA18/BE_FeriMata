package com.fertimata.backend.device;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Liveness view of a device.
 *
 * <p>The timeout is included so the PWA can render "last seen 40s ago, considered offline after 90s"
 * without hard-coding a number the backend owns.
 */
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
