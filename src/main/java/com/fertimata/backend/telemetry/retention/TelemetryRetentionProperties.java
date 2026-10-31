package com.fertimata.backend.telemetry.retention;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retensi telemetri: {@code readingsMaxAge} dari recorded_at, {@code rawMessagesMaxAge} (payload mentah, lebih pendek) dari
 * received_at. {@code batchSize} kecil agar tidak mengunci tabel di Jetson; {@code maxBatchesPerRun} pengaman per putaran.
 */
@Validated
@ConfigurationProperties(prefix = "app.telemetry.retention")
public record TelemetryRetentionProperties(
        boolean enabled,
        @NotNull Duration readingsMaxAge,
        @NotNull Duration rawMessagesMaxAge,
        @Min(100) int batchSize,
        @Min(1) int maxBatchesPerRun,
        @NotNull Duration interval,
        @NotNull Duration initialDelay) {}
