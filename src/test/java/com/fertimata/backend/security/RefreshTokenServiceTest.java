package com.fertimata.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.security.auth.RefreshToken;
import com.fertimata.backend.security.auth.RefreshTokenRepository;
import com.fertimata.backend.security.auth.RefreshTokenService;
import com.fertimata.backend.security.config.JwtProperties;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private final AtomicReference<RefreshToken> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "integration-test-secret-key-at-least-32-characters", "test", Duration.ofMinutes(60), Duration.ofDays(7));
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            TestFixtures.setId(t, 1L);
            stored.set(t);
            return t;
        });
        when(repository.findByTokenHash(any())).thenAnswer(inv -> {
            RefreshToken t = stored.get();
            return t != null && t.getTokenHash().equals(inv.getArgument(0)) ? Optional.of(t) : Optional.empty();
        });
    }

    @Test
    @DisplayName("issuing stores only a hash, with the configured 7-day expiry")
    void issueStoresHash() {
        User user = TestFixtures.operator(3L, "operator");

        RefreshTokenService.Issued issued = service.issue(user, "Mozilla/5.0");

        assertThat(issued.value()).hasSizeGreaterThan(40);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(stored.get().getTokenHash()).isNotEqualTo(issued.value()).hasSize(64);
        assertThat(stored.get().getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("rotating a token returns its owner and makes the token unusable afterwards")
    void rotateIsSingleUse() {
        User user = TestFixtures.operator(3L, "operator");
        String raw = service.issue(user, null).value();

        assertThat(service.rotate(raw)).isSameAs(user);
        assertThat(stored.get().getRevokedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("unknown, expired, and disabled-account tokens are all rejected the same way")
    void rejectsUnusableTokens() {
        assertThatThrownBy(() -> service.rotate("does-not-exist")).isInstanceOf(ApiException.class);

        User user = TestFixtures.operator(3L, "operator");
        String raw = service.issue(user, null).value();
        stored.get().setExpiresAt(NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(ApiException.class);

        String raw2 = service.issue(user, null).value();
        user.setEnabled(false);
        assertThatThrownBy(() -> service.rotate(raw2)).isInstanceOf(ApiException.class);
        assertThat(stored.get().getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("logout with an unknown token is a no-op")
    void revokeUnknownIsNoop() {
        service.revoke("nope");
        service.revoke(null);
        assertThat(stored.get()).isNull();
    }
}
