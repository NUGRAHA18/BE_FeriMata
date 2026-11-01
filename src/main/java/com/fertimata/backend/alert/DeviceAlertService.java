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

/** Stores device-concluded alerts: links them to the registered device/sensor/actuator, dedups repeats, pushes to console. */
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
     * Records one device alert; a repeat is dropped while one of that type is unacknowledged (a shorted SSR must not alert
     * every poll). INFO alerts are events and always recorded. {@code deviceCode} comes from the topic, not the payload.
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

        NewAlert built = alert.build();
        if (built.severity() == AlertSeverity.INFO) {
            // INFO reports a finished event, not a lasting condition: never swallowed by an older unread one.
            alertService.raise(built);
        } else {
            alertService.raiseOnceForDevice(built);
        }
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
