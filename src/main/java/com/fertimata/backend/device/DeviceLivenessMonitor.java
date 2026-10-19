package com.fertimata.backend.device;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically flips devices that have gone quiet to OFFLINE.
 *
 * <p>This exists because "offline" cannot be reported by the thing that is offline. The interval is
 * configuration, not a constant: how often this should run depends on the heartbeat rate the
 * hardware team settles on.
 */
@Component
public class DeviceLivenessMonitor {

    private static final Logger log = LoggerFactory.getLogger(DeviceLivenessMonitor.class);

    private final DeviceService deviceService;

    public DeviceLivenessMonitor(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Scheduled(fixedDelayString = "${app.device.liveness-check-interval}")
    public void markStaleDevicesOffline() {
        List<Device> stale = deviceService.findStaleDevices(Instant.now());
        if (stale.isEmpty()) {
            return;
        }
        log.info("Liveness check found {} device(s) past the offline timeout", stale.size());
        stale.forEach(deviceService::markOffline);
    }
}
