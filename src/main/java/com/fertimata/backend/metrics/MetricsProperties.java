package com.fertimata.backend.metrics;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pengaturan metrik sistem (Protokol-Data.md §6 "Metrik teknis sistem").
 *
 * @param expectedTelemetryInterval interval kirim telemetri edge agent; dasar "pesan yang diharapkan".
 *     TODO(hardware): samakan dengan interval edge agent begitu ditetapkan.
 * @param uptimeBucket lebar slot waktu untuk uptime: slot dianggap hidup bila ada minimal satu pesan
 * @param maxRange rentang terpanjang yang boleh diminta, agar query agregat tetap ringan di Jetson
 * @param energyMetricKey penghitung energi kumulatif (kWh) yang dipakai untuk konsumsi daya
 */
@ConfigurationProperties(prefix = "app.metrics")
public record MetricsProperties(
        Duration expectedTelemetryInterval, Duration uptimeBucket, Duration maxRange, String energyMetricKey) {

    public MetricsProperties {
        expectedTelemetryInterval = expectedTelemetryInterval == null ? Duration.ofSeconds(30) : expectedTelemetryInterval;
        uptimeBucket = uptimeBucket == null ? Duration.ofMinutes(5) : uptimeBucket;
        maxRange = maxRange == null ? Duration.ofDays(92) : maxRange;
        energyMetricKey = energyMetricKey == null || energyMetricKey.isBlank() ? "mains.energy" : energyMetricKey;
    }
}
