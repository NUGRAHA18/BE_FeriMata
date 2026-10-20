package com.fertimata.backend.user;

import com.fertimata.backend.security.auth.CurrentUserProvider;
import com.fertimata.backend.user.dto.ResetPasswordRequest;
import com.fertimata.backend.user.dto.UserCreateRequest;
import com.fertimata.backend.user.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Semua endpoint di sini hanya untuk ADMIN (ditegakkan juga di SecurityConfig). */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Account management (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('" + Role.Names.ADMIN + "')")
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(UserService userService, CurrentUserProvider currentUserProvider) {
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "List accounts")
    public List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one account")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @Operation(summary = "Create an account", description = "The new user is asked to change the password at first sign-in unless mustChangePassword is false.")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse created = userService.create(request, currentUserProvider.requireUsername());
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update name, role or enabled", description = "You cannot change your own role or disable yourself; the last active admin is protected.")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request, currentUserProvider.requireUsername());
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Set a temporary password", description = "Revokes every session of the account; the user must change it at next sign-in.")
    public UserResponse resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        return userService.resetPassword(id, request, currentUserProvider.requireUsername());
    }
}
