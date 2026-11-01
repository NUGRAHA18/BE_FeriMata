package com.fertimata.backend.security.auth;

import com.fertimata.backend.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Pasangan token + akun. Token akses berumur menit (header {@code Authorization}); refresh token berumur hari,
 * hanya ke {@code POST /api/auth/refresh}, dan dirotasi setiap dipakai. */
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
