package com.fertimata.backend.security.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RefreshRequest")
public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest{refreshToken=***}";
    }
}
