package com.fertimata.backend.security.auth;

import com.fertimata.backend.user.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Issued access token plus the account it belongs to, so the PWA can render without a second call. */
@Schema(name = "LoginResponse")
public record LoginResponse(
        @Schema(description = "Signed JWT access token") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the token expires", example = "3600") long expiresIn,
        Instant expiresAt,
        UserResponse user) {}
