package com.fertimata.backend.dev;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DEVELOPMENT ONLY settings for the telemetry simulator.
 *
 * <p>Devices, sensors and actuators are no longer seeded from here: the hardware catalog
 * ({@code app.catalog}) creates them in every profile, so development and the Jetson start from the
 * same inventory. What stays here is only what a simulator needs - which devices to impersonate and
 * the value range of each metric. The ranges are placeholders chosen so the dashboard has something
 * plausible to render; they carry no agronomic meaning and nothing in the backend interprets them.
 */
@ConfigurationProperties(prefix = "app.dev")
public record DevProperties(Simulator simulator) {

    /**
     * @param interval how often each simulated device sends telemetry and a heartbeat
     * @param devices the devices to impersonate
     */
    public record Simulator(boolean enabled, Duration interval, List<SimulatedDevice> devices) {

        public Simulator {
            devices = devices == null ? List.of() : devices;
        }
    }

    /**
     * @param code device code used in the topic; should exist in the hardware catalog
     * @param powerSource value put in the heartbeat's {@code powerSource}; omit for devices that do not
     *     report power
     * @param metrics metrics sent in each telemetry message; a device with none only sends heartbeats
     */
    public record SimulatedDevice(String code, String powerSource, List<Metric> metrics) {

        public SimulatedDevice {
            metrics = metrics == null ? List.of() : metrics;
        }
    }

    public record Metric(String key, BigDecimal min, BigDecimal max, Integer scale) {}
}
