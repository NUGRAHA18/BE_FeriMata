package com.fertimata.backend.telemetry.retention;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kebijakan retensi telemetri.
 *
 * <p>Tanpa kebijakan ini database tumbuh tanpa batas: setiap pesan telemetri disimpan dua kali
 * (payload mentah di {@code telemetry_message} dan satu baris per metrik di {@code sensor_reading}).
 * Payload mentah hanya berguna untuk debugging kontrak, jadi umurnya jauh lebih pendek daripada
 * pembacaan ternormalisasi yang dipakai grafik riwayat.
 *
 * @param enabled matikan untuk menyimpan semuanya selamanya (mis. saat audit)
 * @param readingsMaxAge umur maksimum baris {@code sensor_reading}, dihitung dari {@code recorded_at}
 * @param rawMessagesMaxAge umur maksimum baris {@code telemetry_message}, dihitung dari
 *     {@code received_at}
 * @param batchSize jumlah baris per transaksi hapus; kecil agar tidak mengunci tabel lama-lama di
 *     Jetson yang CPU-nya dibagi dengan inferensi
 * @param maxBatchesPerRun pengaman agar satu putaran tidak berjalan tanpa henti pada tumpukan data
 *     yang sangat besar; sisanya diambil putaran berikutnya
 * @param interval jeda antar putaran pembersihan
 * @param initialDelay jeda sebelum putaran pertama setelah aplikasi menyala
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
