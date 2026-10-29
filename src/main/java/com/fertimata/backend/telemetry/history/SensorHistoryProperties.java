package com.fertimata.backend.telemetry.history;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Ringkasan per jam dan grafik riwayat teragregasi.
 *
 * @param rollupInterval jeda antar putaran job ringkasan; grafik jangka panjang tertinggal paling
 *     lama sebesar ini
 * @param rollupInitialDelay jeda sebelum putaran pertama setelah aplikasi menyala
 * @param rollupLookback jam yang menerima pembacaan dalam rentang ini (menurut {@code received_at})
 *     dihitung ulang tiap putaran; harus lebih besar dari {@code rollupInterval} agar tidak ada
 *     kiriman yang terlewat di antara dua putaran
 * @param defaultRange rentang bila klien tidak mengirim {@code from}
 * @param targetPoints jumlah titik yang dituju saat klien tidak memilih {@code interval}; cukup untuk
 *     layar ponsel tanpa membebani Jetson
 * @param maxPoints batas atas titik untuk interval yang dipilih klien
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
