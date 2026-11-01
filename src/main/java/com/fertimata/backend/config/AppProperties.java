package com.fertimata.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Konfigurasi utama; mode menentukan bean infrastruktur (mock messaging, simulator telemetri hanya di
 * DEVELOPMENT), bukan logika bisnis. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull Mode mode) {

    public enum Mode {
        DEVELOPMENT,
        PRODUCTION
    }

    public boolean isDevelopment() {
        return mode == Mode.DEVELOPMENT;
    }
}
