package com.fertimata.backend.security.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @NotBlank @Size(max = 64) @Schema(example = "operator") String username,
        @NotBlank @Size(max = 128) @Schema(example = "change-me") String password) {


    @Override
    public String toString() {
        return "LoginRequest{username=%s}".formatted(username);
    }
}
