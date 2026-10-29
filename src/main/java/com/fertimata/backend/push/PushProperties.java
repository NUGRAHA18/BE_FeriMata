package com.fertimata.backend.push;

import com.fertimata.backend.alert.AlertSeverity;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Notifikasi push (Web Push) untuk peringatan.
 *
 * <p>Pasangan kunci VAPID dibuat sekali, misalnya dengan {@code npx web-push generate-vapid-keys}, lalu
 * disimpan di {@code .env}. Mengganti kunci memutus semua langganan yang ada: setiap HP harus
 * mengaktifkan notifikasi lagi.
 *
 * @param enabled mati secara default; tanpa kunci VAPID tidak ada yang bisa dikirim
 * @param vapidPublicKey titik P-256 tak terkompresi, base64url (87 karakter)
 * @param vapidPrivateKey kunci privat P-256, base64url (43 karakter); rahasia
 * @param vapidSubject kontak pengirim untuk server push, {@code mailto:...} atau {@code https://...}
 * @param minSeverity peringatan dengan tingkat ini atau lebih tinggi yang dikirim sebagai notifikasi
 * @param ttl berapa lama server push menyimpan notifikasi untuk HP yang sedang mati/offline
 * @param requestTimeout batas waktu satu permintaan ke server push
 * @param allowedEndpointHosts akhiran host server push yang boleh didaftarkan. Backend mengirim POST
 *     ke URL langganan, jadi tanpa daftar ini akun mana pun bisa menyuruh backend menghubungi alamat
 *     internal (SSRF).
 */
@Validated
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(
        boolean enabled,
        String vapidPublicKey,
        String vapidPrivateKey,
        String vapidSubject,
        @NotNull AlertSeverity minSeverity,
        @NotNull Duration ttl,
        @NotNull Duration requestTimeout,
        @NotNull List<String> allowedEndpointHosts) {}
