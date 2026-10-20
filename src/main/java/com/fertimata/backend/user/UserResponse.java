package com.fertimata.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Public view of an account. The password hash is never part of any response. */
@Schema(name = "User", description = "Account")
public record UserResponse(
        Long id,
        @Schema(example = "operator") String username,
        @Schema(example = "Field Operator") String fullName,
        @Schema(example = "OPERATOR") Role role,
        boolean enabled,
        @Schema(description = "True after an admin reset or on a new account: the client should ask for a new password")
                boolean mustChangePassword,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.isEnabled(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
