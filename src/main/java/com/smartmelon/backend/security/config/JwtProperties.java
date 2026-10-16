package com.smartmelon.backend.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pengaturan penandatanganan token JWT.
 *
 * <p>Nilai kunci rahasia (secret) tidak memiliki nilai bawaan dan wajib disediakan melalui
 * lingkungan ({@code JWT_SECRET}). Proses inisialisasi aplikasi akan sengaja digagalkan daripada
 * menggunakan nilai bawaan, karena kunci rahasia bawaan memiliki tingkat keamanan yang setara
 * dengan tanpa autentikasi sama sekali. Algoritma HS256 membutuhkan materi kunci sekurang-kurangnya 256 bit.
 */

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {}
