package com.fertimata.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.security.auth.AppUserDetails;
import com.fertimata.backend.security.auth.AuthService;
import com.fertimata.backend.security.auth.LoginRequest;
import com.fertimata.backend.security.auth.LoginResponse;
import com.fertimata.backend.security.auth.RefreshTokenService;
import com.fertimata.backend.security.jwt.JwtTokenService;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.user.Role;
import com.fertimata.backend.user.User;
import com.fertimata.backend.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("a valid login returns a token and the account, and never the password hash")
    void successfulLogin() {
        User user = TestFixtures.operator(7L, "operator");
        AppUserDetails principal = new AppUserDetails(user);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        Instant expiry = Instant.now().plusSeconds(3600);
        when(jwtTokenService.issue(any())).thenReturn(new JwtTokenService.IssuedToken("token-value", expiry, 3600));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        Instant refreshExpiry = expiry.plusSeconds(7 * 86400);
        when(refreshTokenService.issue(any(), any())).thenReturn(new RefreshTokenService.Issued("refresh-value", refreshExpiry));

        LoginResponse response = authService.login(new LoginRequest("operator", "correct-password"), "test-agent");

        assertThat(response.accessToken()).isEqualTo("token-value");
        assertThat(response.refreshToken()).isEqualTo("refresh-value");
        assertThat(response.refreshExpiresAt()).isEqualTo(refreshExpiry);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.expiresAt()).isEqualTo(expiry);
        assertThat(response.user().username()).isEqualTo("operator");
        assertThat(response.user().role()).isEqualTo(Role.OPERATOR);
        // The response record has no field that could carry a credential.
        assertThat(response.toString()).doesNotContain("notarealhash");
    }

    @Test
    @DisplayName("invalid credentials are rejected and no token is issued")
    void invalidCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("operator", "wrong-password"), null))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtTokenService, never()).issue(any());
    }

    @Test
    @DisplayName("the login request never exposes the password through toString")
    void loginRequestDoesNotLeakPassword() {
        LoginRequest request = new LoginRequest("operator", "super-secret");

        assertThat(request.toString()).contains("operator").doesNotContain("super-secret");
    }
}
