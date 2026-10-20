package com.fertimata.backend.user.dto;

import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Semua field opsional; hanya yang diisi yang diubah. */
@Schema(name = "UserUpdateRequest")
public record UserUpdateRequest(@Size(max = 128) String fullName, Role role, Boolean enabled) {}
