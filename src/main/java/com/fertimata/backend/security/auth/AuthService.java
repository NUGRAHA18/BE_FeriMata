package com.fertimata.backend.security.auth;

import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.common.exception.ErrorCode;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.security.jwt.JwtTokenService;
import com.fertimata.backend.user.User;
import com.fertimata.backend.user.UserRepository;
import com.fertimata.backend.user.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies credentials, mints access tokens, and manages the refresh-token session. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticates a login attempt.
     *
     * @throws AuthenticationException when the credentials are wrong or the account is disabled;
     *     the handler turns this into a 401 with a message that does not reveal which of the two it
     *     was.
     */
    @Transactional
    public LoginResponse login(LoginRequest request, String userAgent) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            // Username is safe to log; the password and the reason are not.
            log.warn("Failed login attempt for username={}", request.username());
            throw ex;
        }

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        User user = userRepository
                .findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.userId()));
        log.info("{} {} signed in", user.getRole(), user.getUsername());
        return issueTokens(user, userAgent);
    }

    /** Menukar refresh token dengan pasangan token baru. Token lama mati setelah ini. */
    @Transactional
    public LoginResponse refresh(RefreshRequest request, String userAgent) {
        User user = refreshTokenService.rotate(request.refreshToken());
        log.debug("Session refreshed for {}", user.getUsername());
        return issueTokens(user, userAgent);
    }

    /** Logout perangkat ini saja. */
    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /**
     * Ganti sandi oleh pemilik akun. Semua sesi lain dicabut dan pasangan token baru dikembalikan,
     * sehingga perangkat yang dipakai mengganti tetap masuk.
     */
    @Transactional
    public LoginResponse changePassword(String username, ChangePasswordRequest request, String userAgent) {
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("Wrong current password on change-password for {}", username);
            throw new ApiException(ErrorCode.AUTHENTICATION_FAILED, "Kata sandi saat ini salah");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Kata sandi baru harus berbeda dari yang lama");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password changed by {}", username);
        return issueTokens(user, userAgent);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String username) {
        return userRepository
                .findByUsername(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    private LoginResponse issueTokens(User user, String userAgent) {
        JwtTokenService.IssuedToken access = jwtTokenService.issue(new AppUserDetails(user));
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user, userAgent);
        return new LoginResponse(
                access.value(),
                "Bearer",
                access.expiresInSeconds(),
                access.expiresAt(),
                refresh.value(),
                refresh.expiresAt(),
                UserResponse.from(user));
    }
}
