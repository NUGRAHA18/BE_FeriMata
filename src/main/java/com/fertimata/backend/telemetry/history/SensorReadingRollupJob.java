package com.fertimata.backend.telemetry.history;

import com.fertimata.backend.telemetry.retention.TelemetryRetentionProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Menjaga {@code sensor_reading_hourly} tetap mengikuti pembacaan mentah.
 *
 * <p>Tidak ikut dimatikan bersama retensi: ringkasan juga mempercepat grafik berbulan-bulan, dan
 * ukurannya kecil (~24 baris per sensor per hari). Urutannya terhadap job retensi tidak penting,
 * karena retensi menghapus data berumur puluhan hari sedangkan job ini meringkas data dalam hitungan
 * menit setelah diterima.
 */
@Component
public class SensorReadingRollupJob {

    private static final Logger log = LoggerFactory.getLogger(SensorReadingRollupJob.class);

    private final SensorHistoryQueries queries;
    private final SensorHistoryProperties properties;
    private final TelemetryRetentionProperties retention;
    private final Clock clock;

    public SensorReadingRollupJob(
            SensorHistoryQueries queries,
            SensorHistoryProperties properties,
            TelemetryRetentionProperties retention,
            Clock clock) {
        this.queries = queries;
        this.properties = properties;
        this.retention = retention;
        this.clock = clock;
        if (properties.rollupLookback().compareTo(properties.rollupInterval()) <= 0) {
            log.warn(
                    "Hourly rollup lookback {} is not longer than its interval {}; late readings may be missed",
                    properties.rollupLookback(),
                    properties.rollupInterval());
        }
    }

    @Scheduled(
            fixedDelayString = "${app.telemetry.history.rollup-interval}",
            initialDelayString = "${app.telemetry.history.rollup-initial-delay}")
    public void run() {
        int written = runOnce(clock.instant());
        log.debug("Hourly rollup refreshed {} bucket(s)", written);
    }

    /**
     * Dipisah dari {@link #run()} agar bisa diuji tanpa scheduler. Satu pernyataan SQL, jadi atomik
     * tanpa transaksi tambahan.
     */
    public int runOnce(Instant now) {
        return queries.rollUpReceivedSince(now.minus(properties.rollupLookback()), notBefore(now));
    }

    /**
     * Jam paling tua yang masih boleh dihitung ulang. Dua jam di atas batas retensi: jam yang sedang
     * dihapus retensi hanya tersisa sebagian, dan menghitungnya ulang akan merusak ringkasan yang utuh.
     */
    Instant notBefore(Instant now) {
        if (!retention.enabled()) {
            return Instant.EPOCH;
        }
        return now.minus(retention.readingsMaxAge()).truncatedTo(ChronoUnit.HOURS).plus(2, ChronoUnit.HOURS);
    }
}
