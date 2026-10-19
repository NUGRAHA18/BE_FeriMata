package com.fertimata.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;

/** Public view of an account. The password hash is never part of any response. */
@Schema(name = "User", description = "Authenticated account")
public record UserResponse(
        Long id,
        @Schema(example = "operator") String username,
        @Schema(example = "Field Operator") String fullName,
        @Schema(example = "OPERATOR") Role role, 
        boolean enabled) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), 
                user.getUsername(), 
                user.getFullName(), 
                user.getRole(), 
                user.isEnabled()
            );
    }
}
