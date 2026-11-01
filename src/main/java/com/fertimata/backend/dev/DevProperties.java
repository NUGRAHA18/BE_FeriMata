package com.fertimata.backend.dev;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** DEVELOPMENT ONLY simulator settings; inventory comes from {@code app.catalog}. Metric ranges are placeholders, not agronomic. */
@ConfigurationProperties(prefix = "app.dev")
public record DevProperties(Simulator simulator) {

    /** {@code interval}: how often each simulated device sends telemetry and a heartbeat. */
    public record Simulator(boolean enabled, Duration interval, List<SimulatedDevice> devices) {

        public Simulator {
            devices = devices == null ? List.of() : devices;
        }
    }

    /** Code should exist in the catalog; omit powerSource if the device reports none; no metrics = heartbeats only. */
    public record SimulatedDevice(String code, String powerSource, List<Metric> metrics) {

        public SimulatedDevice {
            metrics = metrics == null ? List.of() : metrics;
        }
    }

    public record Metric(String key, BigDecimal min, BigDecimal max, Integer scale) {}
}
