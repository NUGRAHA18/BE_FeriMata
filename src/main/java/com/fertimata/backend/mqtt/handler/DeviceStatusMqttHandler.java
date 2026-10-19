package com.fertimata.backend.mqtt.handler;

import com.fertimata.backend.actuator.ActuatorService;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.mqtt.MqttMessageHandler;
import com.fertimata.backend.mqtt.TopicKind;
import com.fertimata.backend.mqtt.payload.DeviceStatusPayload;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles device heartbeat and state messages.
 *
 * <p>Any message on this topic counts as proof of life, including an empty one - which is what makes
 * a plain heartbeat possible without inventing a payload contract for it.
 */
@Component
public class DeviceStatusMqttHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusMqttHandler.class);

    private final DeviceService deviceService;
    private final ActuatorService actuatorService;
    private final JsonSupport jsonSupport;

    public DeviceStatusMqttHandler(
            DeviceService deviceService, ActuatorService actuatorService, JsonSupport jsonSupport) {
        this.deviceService = deviceService;
        this.actuatorService = actuatorService;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public TopicKind handles() {
        return TopicKind.STATUS;
    }

    @Override
    @Transactional
    public void handle(String deviceCode, String payload) {
        DeviceStatusPayload status = parse(deviceCode, payload);

        Optional<Device> device = deviceService.resolveForIngest(deviceCode);
        if (device.isEmpty()) {
            log.warn("Ignoring status message from unknown device {}", deviceCode);
            return;
        }

        Instant reportedAt = status != null && status.timestamp() != null ? status.timestamp() : Instant.now();
        deviceService.recordContact(device.get(), reportedAt, status == null ? null : status.metadata());
        log.debug("Heartbeat from {}", deviceCode);

        if (status != null && status.powerSource() != null) {
            deviceService.reportPowerSource(device.get(), status.powerSource(), reportedAt);
        }

        if (status != null && status.actuators() != null) {
            status.actuators()
                    .forEach((code, state) -> actuatorService.reportState(device.get(), code, state, reportedAt));
        }
    }

    /** A status message may legitimately be empty, so a parse failure downgrades to a bare heartbeat. */
    private DeviceStatusPayload parse(String deviceCode, String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return jsonSupport.fromJson(payload, DeviceStatusPayload.class);
        } catch (RuntimeException ex) {
            log.warn("Status message from {} was not valid JSON; treating it as a bare heartbeat", deviceCode);
            return null;
        }
    }
}
