package com.fertimata.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Akun operator pertama dari environment, dibuat sekali saat tabel user kosong bila {@code enabled};
 * kredensial tidak pernah di-log. */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(boolean enabled, String operatorUsername, String operatorPassword,
        String operatorFullName) {

    @Override
    public String toString() {
        return "BootstrapProperties{enabled=%s, operatorUsername=%s}".formatted(enabled, operatorUsername);
    }
}
