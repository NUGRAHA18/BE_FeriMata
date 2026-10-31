package com.fertimata.backend.catalog;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hardware inventory as config (default {@code classpath:hardware/fertimata-rev-a.yml}, env {@code HARDWARE_CATALOG_LOCATION}).
 * Wiring in {@code metadata} is informational only; the edge agent's address map is the source of truth.
 */
@ConfigurationProperties(prefix = "app.catalog")
public record HardwareCatalogProperties(boolean enabled, String name, List<DeviceEntry> devices) {

    public HardwareCatalogProperties {
        devices = devices == null ? List.of() : devices;
    }

    public record DeviceEntry(
            String code,
            String name,
            String type,
            String description,
            Map<String, String> metadata,
            List<SensorEntry> sensors,
            List<ActuatorEntry> actuators) {

        public DeviceEntry {
            sensors = sensors == null ? List.of() : sensors;
            actuators = actuators == null ? List.of() : actuators;
        }
    }

    /** {@code enabled} defaults to true; false for a channel that is wired but not yet trusted. */
    public record SensorEntry(
            String code,
            String metricKey,
            String name,
            String type,
            String unit,
            String description,
            Boolean enabled,
            Map<String, String> metadata) {}

    public record ActuatorEntry(
            String code,
            String name,
            String type,
            String description,
            Integer maxRunSeconds,
            Boolean enabled,
            Map<String, String> metadata) {}
}
