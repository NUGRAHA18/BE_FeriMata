package com.fertimata.backend.device;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Flips quiet devices to OFFLINE ("offline" cannot self-report); the interval is config, tied to the heartbeat rate. */
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
