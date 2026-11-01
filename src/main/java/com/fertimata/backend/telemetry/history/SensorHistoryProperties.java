package com.fertimata.backend.telemetry.history;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Ringkasan per jam dan grafik. {@code rollupLookback} (menurut received_at) harus > {@code rollupInterval} agar kiriman
 * tidak terlewat; {@code targetPoints} dipakai bila klien tidak memilih interval, {@code maxPoints} batas atas.
 */
@Validated
@ConfigurationProperties(prefix = "app.telemetry.history")
public record SensorHistoryProperties(
        @NotNull Duration rollupInterval,
        @NotNull Duration rollupInitialDelay,
        @NotNull Duration rollupLookback,
        @NotNull Duration defaultRange,
        @Min(10) int targetPoints,
        @Min(10) int maxPoints) {}
