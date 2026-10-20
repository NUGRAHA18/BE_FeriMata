package com.fertimata.backend.security.auth;

import com.fertimata.backend.common.response.ApiError;
import com.fertimata.backend.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Sign-in, session refresh, password change")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Sign in",
            description = "Returns a short-lived JWT access token (Authorization: Bearer) and a refresh token that keeps the session alive for days.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.ok(authService.login(request, userAgent));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Exchange a refresh token for a new token pair",
            description = "The refresh token is single-use: the previous one is revoked. 401 when it is unknown, expired, revoked or the account is disabled.")
    @ApiResponse(responseCode = "200", description = "New token pair")
    @ApiResponse(
            responseCode = "401",
            description = "Refresh token no longer valid; sign in again",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.ok(authService.refresh(request, userAgent));
    }

    @PostMapping("/logout")
    @Operation(summary = "End this device's session", description = "Revokes the given refresh token. Always 204, even for an unknown token.")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @Operation(
            summary = "Change the signed-in account's password",
            description = "Requires the current password. Every other session of the account is revoked; a fresh token pair is returned for this device.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<LoginResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.ok(authService.changePassword(currentUserProvider.requireUsername(), request, userAgent));
    }

    @GetMapping("/me")
    @Operation(summary = "Describe the account behind the current token")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(authService.currentUser(currentUserProvider.requireUsername()));
    }
}
