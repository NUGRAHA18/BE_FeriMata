package com.fertimata.backend.security.auth;

import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.security.jwt.JwtTokenService;
import com.fertimata.backend.user.UserRepository;
import com.fertimata.backend.user.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies credentials and mints access tokens. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    /**
     * Authenticates a login attempt.
     *
     * @throws AuthenticationException when the credentials are wrong or the account is disabled;
     *     the handler turns this into a 401 with a message that does not reveal which of the two it
     *     was.
     */
    public LoginResponse login(LoginRequest request) {
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
        JwtTokenService.IssuedToken token = jwtTokenService.issue(principal);
        log.info("Operator {} signed in", principal.getUsername());

        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                token.expiresAt(),
                UserResponse.from(userRepository
                        .findById(principal.userId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", principal.userId()))));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String username) {
        return userRepository
                .findByUsername(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }
}
