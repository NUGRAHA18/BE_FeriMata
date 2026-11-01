package com.fertimata.backend.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Layanan AI (Flask, {@code Ai-code/main.py}); {@code baseUrl} kosong = tidak dipanggil. {@code timeout} longgar karena
 * YOLO + fuzzy di Jetson bisa puluhan detik; foto patroli lebih tua dari {@code photoMaxAge} tidak dikirim. */
@ConfigurationProperties(prefix = "app.ai.service")
public record AiServiceProperties(String baseUrl, Duration timeout, Integer irrigationPhotos, Duration photoMaxAge) {

    public AiServiceProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.trim().replaceAll("/+$", "");
        timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
        irrigationPhotos = irrigationPhotos == null ? 3 : irrigationPhotos;
        photoMaxAge = photoMaxAge == null ? Duration.ofHours(24) : photoMaxAge;
    }

    public boolean configured() {
        return baseUrl != null;
    }
}
