package com.fertimata.backend.security.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePasswordRequest")
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 8, max = 128, message = "Kata sandi baru minimal 8 karakter") String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest{***}";
    }
}
