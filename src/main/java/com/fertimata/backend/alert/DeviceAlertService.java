package com.fertimata.backend.alert;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.mqtt.payload.DeviceAlertPayload;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores alerts an edge device concluded on its own.
 *
 * <p>The type vocabulary is the device's. The backend adds what only it can: attaching the alert to
 * the registered device, sensor and actuator, deduplicating a fault the device keeps reporting, and
 * pushing it to the console.
 */
@Service
public class DeviceAlertService {

    private static final Logger log = LoggerFactory.getLogger(DeviceAlertService.class);

    private static final int TYPE_LENGTH = 64;
    private static final int TITLE_LENGTH = 160;
    private static final int MESSAGE_LENGTH = 1000;

    private final DeviceService deviceService;
    private final SensorRepository sensorRepository;
    private final ActuatorRepository actuatorRepository;
    private final AlertService alertService;

    public DeviceAlertService(
            DeviceService deviceService,
            SensorRepository sensorRepository,
            ActuatorRepository actuatorRepository,
            AlertService alertService) {
        this.deviceService = deviceService;
        this.sensorRepository = sensorRepository;
        this.actuatorRepository = actuatorRepository;
        this.alertService = alertService;
    }

    /**
     * Records one device-reported alert.
     *
     * <p>While an alert of the same type is still unacknowledged for the device, a repeat is dropped:
     * an SSR that stays shorted must not produce an alert every polling cycle. Once an operator
     * acknowledges it, the next report raises it again.
     *
     * @param deviceCode taken from the topic, not the payload
     */
    @Transactional
    public void record(String deviceCode, DeviceAlertPayload payload) {
        if (payload == null) {
            return;
        }
        Optional<Device> resolved = deviceService.resolveForIngest(deviceCode);
        if (resolved.isEmpty()) {
            log.warn("Ignoring alert from unknown device {}", deviceCode);
            return;
        }
        Device device = resolved.get();
        Instant reportedAt = payload.timestamp() == null ? Instant.now() : payload.timestamp();
        deviceService.recordContact(device, reportedAt, null);

        String type = truncate(blankToNull(payload.type()) == null ? AlertType.DEVICE_REPORTED : payload.type().trim(),
                TYPE_LENGTH);
        String title = truncate(blankToNull(payload.title()) == null ? type : payload.title().trim(), TITLE_LENGTH);
        String message = truncate(
                blankToNull(payload.message()) == null
                        ? "Device %s reported %s".formatted(deviceCode, type)
                        : payload.message().trim(),
                MESSAGE_LENGTH);

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (payload.metadata() != null) {
            metadata.putAll(payload.metadata());
        }
        metadata.put("reportedAt", reportedAt.toString());

        NewAlert.Builder alert = NewAlert.builder(type, severity(payload.severity()))
                .title(title)
                .message(message)
                .source(AlertSource.DEVICE)
                .device(device)
                .metadata(metadata);
        findSensor(device, payload.sensorCode()).ifPresent(alert::sensor);
        findActuator(device, payload.actuatorCode()).ifPresent(alert::actuator);

        alertService.raiseOnceForDevice(alert.build());
    }

    private Optional<Sensor> findSensor(Device device, String code) {
        if (blankToNull(code) == null) {
            return Optional.empty();
        }
        Optional<Sensor> sensor = sensorRepository.findByDeviceIdAndCode(device.getId(), code.trim());
        if (sensor.isEmpty()) {
            log.debug("Alert from {} referenced unknown sensor {}", device.getDeviceCode(), code);
        }
        return sensor;
    }

    private Optional<Actuator> findActuator(Device device, String code) {
        if (blankToNull(code) == null) {
            return Optional.empty();
        }
        Optional<Actuator> actuator = actuatorRepository.findByDeviceIdAndCode(device.getId(), code.trim());
        if (actuator.isEmpty()) {
            log.debug("Alert from {} referenced unknown actuator {}", device.getDeviceCode(), code);
        }
        return actuator;
    }

    private static AlertSeverity severity(String raw) {
        if (raw == null) {
            return AlertSeverity.WARNING;
        }
        try {
            return AlertSeverity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AlertSeverity.WARNING;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
