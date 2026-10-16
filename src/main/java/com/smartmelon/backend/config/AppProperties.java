package com.smartmelon.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Konfigurasi utama aplikasi. 
 *
 * di mana mock messaging dan simulator telementri hanya aktif pada mode tersebut. 
 * Nilai ini tidak mengontrol logika bisnis, melainkan menentukan bean infrastruktur yang dibuat
 */
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
