package com.fertimata.backend.catalog;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The hardware inventory, as configuration.
 *
 * <p>This is how a hardware revision reaches the backend without touching Java: devices, their
 * sensors and their actuators are listed in a YAML file (by default
 * {@code classpath:hardware/fertimata-rev-a.yml}, overridable with {@code HARDWARE_CATALOG_LOCATION}).
 * {@link HardwareCatalogSynchronizer} creates whatever is missing at startup.
 *
 * <p>The catalog describes what exists and how operators should see it. It does not describe wiring:
 * Modbus addresses and GPIO pins in {@code metadata} are informational copies, and the edge agent's
 * own address map remains the source of truth.
 *
 * @param enabled create missing catalog rows at startup
 * @param name label of the hardware revision, for logs only
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

    /**
     * @param enabled defaults to true; set false for a channel that is wired but not yet trusted
     */
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
