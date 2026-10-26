package com.fertimata.backend.metrics;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pengaturan metrik sistem (Protokol-Data.md §6 "Metrik teknis sistem").
 *
 * @param expectedTelemetryInterval interval kirim telemetri untuk device yang tidak tercantum di
 *     {@code deviceIntervals}; dasar "pesan yang diharapkan"
 * @param uptimeBucket lebar slot waktu untuk uptime: slot dianggap hidup bila ada minimal satu pesan
 * @param maxRange rentang terpanjang yang boleh diminta, agar query agregat tetap ringan di Jetson
 * @param energyMetricKey penghitung energi kumulatif (kWh) yang dipakai untuk konsumsi daya
 * @param deviceIntervals interval per device, dari katalog hardware. Edge agent mengirim satu pesan per
 *     kelompok polling, jadi device dengan dua kelompok (mis. udara/cahaya 60 s dan tanah 300 s)
 *     mencantumkan keduanya dan pesan yang diharapkan adalah jumlahnya
 */
@ConfigurationProperties(prefix = "app.metrics")
public record MetricsProperties(
        Duration expectedTelemetryInterval,
        Duration uptimeBucket,
        Duration maxRange,
        String energyMetricKey,
        List<DeviceIntervals> deviceIntervals) {

    public MetricsProperties {
        expectedTelemetryInterval = expectedTelemetryInterval == null ? Duration.ofSeconds(30) : expectedTelemetryInterval;
        uptimeBucket = uptimeBucket == null ? Duration.ofMinutes(5) : uptimeBucket;
        maxRange = maxRange == null ? Duration.ofDays(92) : maxRange;
        energyMetricKey = energyMetricKey == null || energyMetricKey.isBlank() ? "mains.energy" : energyMetricKey;
        deviceIntervals = deviceIntervals == null
                ? List.of()
                : deviceIntervals.stream().filter(Objects::nonNull).toList();
    }

    /** Interval kirim telemetri sebuah device; interval global bila device tidak tercantum. */
    public List<Duration> intervalsFor(String deviceCode) {
        return deviceIntervals.stream()
                .filter(entry -> entry.device() != null && entry.device().equalsIgnoreCase(deviceCode))
                .findFirst()
                .map(DeviceIntervals::intervals)
                .filter(intervals -> !intervals.isEmpty())
                .orElse(List.of(expectedTelemetryInterval));
    }

    /**
     * @param device kode device katalog
     * @param intervals satu interval per kelompok polling yang mengirim pesan telemetri sendiri
     */
    public record DeviceIntervals(String device, List<Duration> intervals) {

        public DeviceIntervals {
            intervals = intervals == null
                    ? List.of()
                    : intervals.stream().filter(Objects::nonNull).toList();
        }
    }
}
