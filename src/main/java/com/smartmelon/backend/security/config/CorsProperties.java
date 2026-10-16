package com.smartmelon.backend.security.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pengaturan berbagi sumber daya lintas asal (CORS) untuk PWA.
 *
 * <p>Origin selalu didefinisikan sebagai daftar eksplisit. Tanda bintang (<i>wildcard</i>) tidak pernah
 * digunakan karena API ini memanfaatkan kredensial, serta domain frontend produksi merupakan
 * detail repositori penyebaran (<i>deployment</i>) yang dikonfigurasi melalui lingkungan ({@code environment}).
 */

@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        Duration maxAge) {}
