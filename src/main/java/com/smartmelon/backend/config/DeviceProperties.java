package com.smartmelon.backend.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Mengatur policy untuk liveness dan registrasi device.
 *
 * @param offlineTimeout batas waktu device boleh tidak mengirim heartbeat.
 *        Kalau sudah melewati batas ini tanpa ada heartbeat, device dianggap OFFLINE.
 *        Deteksi ini sengaja menggunakan timeout karena kalau device mati atau kehilangan power,
 *        device tidak sempat mengirim pesan bahwa dirinya sudah offline.
 *        TODO(hardware): nilainya sebaiknya dibuat beberapa kali dari heartbeat interval
 *        yang benar-benar digunakan oleh edge device.
 *
 * @param livenessCheckInterval seberapa sering backend mengecek status device.
 *
 * @param autoRegister jika true, device yang belum dikenal akan otomatis didaftarkan.
 *        Cocok untuk testing, tapi sebaiknya false di production.
 */
@Validated
@ConfigurationProperties(prefix = "app.device")
public record DeviceProperties(
        @NotNull Duration offlineTimeout, @NotNull Duration livenessCheckInterval, boolean autoRegister) {}
