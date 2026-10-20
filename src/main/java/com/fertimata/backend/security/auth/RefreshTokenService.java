package com.fertimata.backend.security.auth;

import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.common.exception.ErrorCode;
import com.fertimata.backend.security.config.JwtProperties;
import com.fertimata.backend.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menerbitkan, merotasi, dan mencabut refresh token.
 *
 * <p>Token akses (JWT) tetap pendek umurnya dan tidak bisa dicabut; refresh token-lah yang
 * memberi "sesi 7 hari" dan bisa dicabut kapan saja (ganti sandi, akun dinonaktifkan, logout).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 48;
    private static final Duration KEEP_REVOKED_FOR = Duration.ofDays(30);

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Token baru untuk satu perangkat. Nilai mentah dikembalikan sekali dan tidak disimpan. */
    @Transactional
    public Issued issue(User user, String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now(clock).plus(properties.refreshTokenTtl()));
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length())));
        repository.save(token);
        return new Issued(raw, token.getExpiresAt());
    }

    /**
     * Memakai refresh token: yang lama dicabut, pemiliknya dikembalikan agar pemanggil menerbitkan
     * pasangan token baru.
     *
     * @throws ApiException AUTHENTICATION_FAILED bila token tidak dikenal, kedaluwarsa, sudah dicabut,
     *     atau akunnya dinonaktifkan - semuanya satu pesan, agar token yang bocor tidak bisa diraba.
     */
    @Transactional
    public User rotate(String raw) {
        Instant now = Instant.now(clock);
        RefreshToken token = repository
                .findByTokenHash(hash(raw))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_FAILED, "Session is no longer valid"));
        if (!token.getUser().isEnabled()) {
            token.setRevokedAt(now);
            throw new ApiException(ErrorCode.AUTHENTICATION_FAILED, "Session is no longer valid");
        }
        token.setRevokedAt(now);
        return token.getUser();
    }

    /** Logout satu perangkat. Token yang tidak dikenal diabaikan: tujuannya sudah tercapai. */
    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        Optional<RefreshToken> token = repository.findByTokenHash(hash(raw));
        token.filter(t -> t.getRevokedAt() == null).ifPresent(t -> t.setRevokedAt(Instant.now(clock)));
    }

    /** Semua sesi akun berakhir: setelah ganti sandi, reset sandi, atau akun dinonaktifkan. */
    @Transactional
    public int revokeAllForUser(Long userId) {
        int count = repository.revokeAllForUser(userId, Instant.now(clock));
        if (count > 0) {
            log.info("Revoked {} refresh token(s) of user id={}", count, userId);
        }
        return count;
    }

    @Scheduled(fixedDelayString = "PT24H", initialDelayString = "PT10M")
    @Transactional
    public void purgeStale() {
        Instant now = Instant.now(clock);
        int removed = repository.deleteStale(now, now.minus(KEEP_REVOKED_FOR));
        if (removed > 0) {
            log.info("Removed {} stale refresh token(s)", removed);
        }
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /** Refresh token yang baru diterbitkan. {@code value} dikirim ke klien tepat satu kali. */
    public record Issued(String value, Instant expiresAt) {}
}
