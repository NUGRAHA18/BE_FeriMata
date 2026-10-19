package com.fertimata.backend.catalog;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceRepository;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates catalog devices, sensors and actuators that do not exist yet.
 *
 * <p><b>Create-only.</b> A row that already exists is never modified: once an operator has renamed a
 * sensor, disabled a pump or changed a run-time limit through the API, a restart must not undo it.
 * Removing something from the catalog does not delete it either - history references it.
 *
 * <p>Runs at bean initialisation rather than from a runner, for the same reason the development
 * seeder used to: inbound MQTT and the simulator start when the context refreshes, and whichever
 * arrives first would otherwise auto-register a device under a placeholder name.
 */
@Component
@ConditionalOnProperty(prefix = "app.catalog", name = "enabled", havingValue = "true")
public class HardwareCatalogSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(HardwareCatalogSynchronizer.class);

    private final HardwareCatalogProperties catalog;
    private final DeviceRepository deviceRepository;
    private final SensorRepository sensorRepository;
    private final ActuatorRepository actuatorRepository;
    private final JsonSupport jsonSupport;
    private final TransactionTemplate transactionTemplate;

    public HardwareCatalogSynchronizer(
            HardwareCatalogProperties catalog,
            DeviceRepository deviceRepository,
            SensorRepository sensorRepository,
            ActuatorRepository actuatorRepository,
            JsonSupport jsonSupport,
            TransactionTemplate transactionTemplate) {
        this.catalog = catalog;
        this.deviceRepository = deviceRepository;
        this.sensorRepository = sensorRepository;
        this.actuatorRepository = actuatorRepository;
        this.jsonSupport = jsonSupport;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void synchronize() {
        if (catalog.devices().isEmpty()) {
            log.info("Hardware catalog {} is enabled but lists no devices", label());
            return;
        }
        try {
            Result result = transactionTemplate.execute(status -> apply());
            log.info(
                    "Hardware catalog {} applied: {} device(s), {} sensor(s), {} actuator(s) created",
                    label(),
                    result.devices(),
                    result.sensors(),
                    result.actuators());
        } catch (RuntimeException ex) {
            // A catalog mistake must be loud, but it must not keep the operator console from starting.
            log.error("Hardware catalog {} could not be applied: {}", label(), ex.getMessage(), ex);
        }
    }

    Result apply() {
        int devices = 0;
        int sensors = 0;
        int actuators = 0;
        for (HardwareCatalogProperties.DeviceEntry entry : catalog.devices()) {
            if (isBlank(entry.code())) {
                log.warn("Skipping a catalog device without a code");
                continue;
            }
            Device device = deviceRepository.findByDeviceCode(entry.code()).orElse(null);
            if (device == null) {
                device = new Device(entry.code(), orDefault(entry.name(), entry.code()), entry.type());
                device.setDescription(entry.description());
                device.setMetadata(json(entry.metadata()));
                device = deviceRepository.save(device);
                devices++;
            }
            for (HardwareCatalogProperties.SensorEntry sensor : entry.sensors()) {
                sensors += createSensor(device, sensor) ? 1 : 0;
            }
            for (HardwareCatalogProperties.ActuatorEntry actuator : entry.actuators()) {
                actuators += createActuator(device, actuator) ? 1 : 0;
            }
        }
        return new Result(devices, sensors, actuators);
    }

    private boolean createSensor(Device device, HardwareCatalogProperties.SensorEntry entry) {
        if (isBlank(entry.code()) || isBlank(entry.metricKey())) {
            log.warn("Skipping a sensor on {} without a code or metric key", device.getDeviceCode());
            return false;
        }
        if (sensorRepository.findByDeviceIdAndCode(device.getId(), entry.code()).isPresent()) {
            return false;
        }
        if (sensorRepository.findByDeviceIdAndMetricKey(device.getId(), entry.metricKey()).isPresent()) {
            // Typically an auto-registered placeholder that arrived first. Left for the operator to
            // review rather than silently replaced.
            log.warn(
                    "Catalog sensor {} on {} not created: metric key {} is already mapped",
                    entry.code(),
                    device.getDeviceCode(),
                    entry.metricKey());
            return false;
        }
        Sensor sensor = new Sensor(
                device, entry.code(), entry.metricKey(), orDefault(entry.name(), entry.code()), entry.type(),
                entry.unit());
        sensor.setDescription(entry.description());
        sensor.setEnabled(entry.enabled() == null || entry.enabled());
        sensor.setMetadata(json(entry.metadata()));
        sensorRepository.save(sensor);
        return true;
    }

    private boolean createActuator(Device device, HardwareCatalogProperties.ActuatorEntry entry) {
        if (isBlank(entry.code())) {
            log.warn("Skipping an actuator on {} without a code", device.getDeviceCode());
            return false;
        }
        if (actuatorRepository.findByDeviceIdAndCode(device.getId(), entry.code()).isPresent()) {
            return false;
        }
        Actuator actuator =
                new Actuator(device, entry.code(), orDefault(entry.name(), entry.code()), entry.type());
        actuator.setDescription(entry.description());
        actuator.setEnabled(entry.enabled() == null || entry.enabled());
        actuator.setMaxRunSeconds(entry.maxRunSeconds() != null && entry.maxRunSeconds() > 0
                ? entry.maxRunSeconds()
                : null);
        actuator.setMetadata(json(entry.metadata()));
        actuatorRepository.save(actuator);
        return true;
    }

    private String json(Map<String, String> metadata) {
        return metadata == null || metadata.isEmpty() ? null : jsonSupport.toJson(metadata);
    }

    private String label() {
        return isBlank(catalog.name()) ? "(unnamed)" : catalog.name();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    record Result(int devices, int sensors, int actuators) {}
}
