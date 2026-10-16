package com.smartmelon.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
* Pembuatan akun saat pertama kali aplikasi dijalankan.
* Data login diambil langsung dari environment, cuma dipakai sekali pas tabel user masih kosong, dan
* tidak pernah dicatat ke log sama sekali.
 *
 * @param enabled status aktif untuk buat akun operator pertama pas belum ada akun sama sekali
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(boolean enabled, String operatorUsername, String operatorPassword,
        String operatorFullName) {

    @Override
    public String toString() {
        return "BootstrapProperties{enabled=%s, operatorUsername=%s}".formatted(enabled, operatorUsername);
    }
}
