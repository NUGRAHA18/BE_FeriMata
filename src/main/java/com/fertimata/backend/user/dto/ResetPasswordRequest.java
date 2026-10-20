package com.fertimata.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ResetPasswordRequest")
public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 128, message = "Kata sandi minimal 8 karakter") String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordRequest{***}";
    }
}
