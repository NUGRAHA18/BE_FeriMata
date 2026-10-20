package com.fertimata.backend.user.dto;

import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UserCreateRequest")
public record UserCreateRequest(
        @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[a-zA-Z0-9._-]+", message = "Hanya huruf, angka, titik, strip, garis bawah")
                @Schema(example = "pak.budi") String username,
        @NotBlank @Size(min = 8, max = 128, message = "Kata sandi minimal 8 karakter") String password,
        @Size(max = 128) @Schema(example = "Budi Santoso") String fullName,
        @NotNull Role role,
        @Schema(description = "Default true: the user must change the password at first sign-in") Boolean mustChangePassword) {

    @Override
    public String toString() {
        return "UserCreateRequest{username=%s, role=%s}".formatted(username, role);
    }
}
