package com.fertimata.backend.security.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS untuk PWA: origin selalu daftar eksplisit tanpa wildcard karena API memakai kredensial;
 * domain produksi diatur lewat environment. */

@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        Duration maxAge) {}
