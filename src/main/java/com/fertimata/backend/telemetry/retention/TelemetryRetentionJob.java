package com.fertimata.backend.telemetry.retention;

import java.time.Instant;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pekerjaan berkala yang menjaga ukuran tabel telemetri tetap terbatas.
 *
 * <p>Pembacaan ternormalisasi disimpan selama {@code readings-max-age}; payload mentah hanya selama
 * {@code raw-messages-max-age}. Keduanya dihapus per batch, dan satu putaran berhenti setelah
 * {@code max-batches-per-run} agar tidak pernah memonopoli database. Tumpukan yang sangat besar
 * (mis. database yang sudah terlanjur membengkak) habis dalam beberapa putaran.
 */
@Component
@ConditionalOnProperty(prefix = "app.telemetry.retention", name = "enabled", havingValue = "true")
public class TelemetryRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(TelemetryRetentionJob.class);

    private final TelemetryRetentionService service;
    private final TelemetryRetentionProperties properties;

    public TelemetryRetentionJob(TelemetryRetentionService service, TelemetryRetentionProperties properties) {
        this.service = service;
        this.properties = properties;
        log.info(
                "Telemetry retention active: readings kept {}, raw messages kept {}, checked every {}",
                properties.readingsMaxAge(),
                properties.rawMessagesMaxAge(),
                properties.interval());
    }

    @Scheduled(
            fixedDelayString = "${app.telemetry.retention.interval}",
            initialDelayString = "${app.telemetry.retention.initial-delay}")
    public void run() {
        Instant now = Instant.now();
        RunResult result = runOnce(now);
        if (result.readings() > 0 || result.messages() > 0) {
            log.info(
                    "Telemetry retention removed {} reading(s) and {} raw message(s){}",
                    result.readings(),
                    result.messages(),
                    result.truncated() ? " (more remain; continuing next run)" : "");
        }
    }

    /** Dipisah dari {@link #run()} agar bisa diuji tanpa scheduler. */
    RunResult runOnce(Instant now) {
        int batch = properties.batchSize();
        Instant readingsCutoff = now.minus(properties.readingsMaxAge());
        Instant messagesCutoff = now.minus(properties.rawMessagesMaxAge());

        // Pembacaan dulu: setelah pembacaan tua hilang, pesan mentahnya tidak dirujuk siapa pun.
        Drain readings = drain(() -> service.purgeReadingsBatch(readingsCutoff, batch), batch);
        Drain messages = drain(() -> service.purgeMessagesBatch(messagesCutoff, batch), batch);
        return new RunResult(readings.deleted(), messages.deleted(), readings.truncated() || messages.truncated());
    }

    private Drain drain(IntSupplier batchDelete, int batchSize) {
        long deleted = 0;
        for (int i = 0; i < properties.maxBatchesPerRun(); i++) {
            int removed = batchDelete.getAsInt();
            deleted += removed;
            if (removed < batchSize) {
                return new Drain(deleted, false);
            }
        }
        return new Drain(deleted, true);
    }

    private record Drain(long deleted, boolean truncated) {}

    record RunResult(long readings, long messages, boolean truncated) {}
}
