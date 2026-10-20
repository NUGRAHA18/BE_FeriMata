package com.fertimata.backend.security.auth;

import com.fertimata.backend.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Pasangan token plus akun pemiliknya.
 *
 * <p>Token akses pendek umurnya (menit) dan dikirim di header {@code Authorization}. Refresh token
 * panjang umurnya (hari) dan hanya dikirim ke {@code POST /api/auth/refresh}; setiap pemakaian
 * menghasilkan pasangan baru dan mematikan yang lama.
 */
@Schema(name = "LoginResponse")
public record LoginResponse(
        @Schema(description = "Signed JWT access token") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the access token expires", example = "3600") long expiresIn,
        Instant expiresAt,
        @Schema(description = "Opaque refresh token; send it to /api/auth/refresh before expiresAt passes") String refreshToken,
        @Schema(description = "When the refresh token, and with it the session, ends") Instant refreshExpiresAt,
        UserResponse user) {

    @Override
    public String toString() {
        return "LoginResponse{user=%s, expiresAt=%s, refreshExpiresAt=%s}".formatted(user, expiresAt, refreshExpiresAt);
    }
}
