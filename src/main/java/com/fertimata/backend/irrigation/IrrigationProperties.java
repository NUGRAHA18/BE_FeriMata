package com.fertimata.backend.irrigation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Penyiraman terjadwal. {@code enabled=false} menghentikan jadwal tanpa menghapusnya; jadwal terlewat lebih dari {@code grace}
 * dicatat MISSED. Lama siram selalu durasi jadwal isian petani (keputusan 4 Nov 2026).
 */
@Validated
@ConfigurationProperties(prefix = "app.irrigation")
public record IrrigationProperties(
        boolean enabled,
        @NotBlank String actuatorCode,
        @NotNull Duration checkInterval,
        @NotNull Duration grace) {}
