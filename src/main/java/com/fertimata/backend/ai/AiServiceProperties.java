package com.fertimata.backend.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alamat layanan AI (Flask tim AI, {@code Ai-code/main.py}) yang dipanggil backend.
 *
 * @param baseUrl mis. {@code http://host.docker.internal:5000}; kosong = backend tidak memanggil layanan AI
 * @param timeout batas tunggu satu panggilan; YOLO + fuzzy di Jetson bisa butuh puluhan detik
 * @param irrigationPhotos foto patroli terbaru yang ikut dikirim ke {@code run_irrigation} (deteksi penyakit)
 * @param photoMaxAge foto lebih tua dari ini tidak dikirim
 */
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
