package com.fertimata.backend.actuator;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Berapa lama backend menunggu jawaban (ACK) alat untuk sebuah perintah.
 *
 * <p>Edge agent menjawab segera, termasuk perintah panjang seperti patroli yang dijawab saat dimulai
 * (kontrak MQTT §4.3), jadi dua menit sudah longgar. Tanpa batas ini, perintah ke alat yang mati
 * tampil "Terkirim, menunggu jawaban alat" selamanya.
 *
 * @param timeout perintah PENDING/SENT lebih tua dari ini ditandai gagal dengan kode NO_RESPONSE
 * @param checkInterval jeda pemeriksaan
 * @param alertWindow perintah yang macet lebih lama dari ini ditutup tanpa peringatan (mis. sisa uji
 *     lama setelah backend menyala lagi), supaya pemeriksaan pertama tidak membanjiri peringatan
 */
@Validated
@ConfigurationProperties(prefix = "app.actuator.response")
public record CommandResponseProperties(
        @NotNull Duration timeout, @NotNull Duration checkInterval, @NotNull Duration alertWindow) {}
