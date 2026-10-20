package com.fertimata.backend.security.auth;

import com.fertimata.backend.common.domain.BaseEntity;
import com.fertimata.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Satu sesi perangkat. Hanya hash token yang disimpan; nilai mentahnya hanya pernah ada di klien.
 *
 * <p>Dirotasi setiap kali dipakai: token lama di-revoke dan yang baru diterbitkan, sehingga token
 * yang bocor paling lama berguna sampai pemakaian berikutnya oleh pemilik aslinya.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    @Override
    public String toString() {
        return "RefreshToken{id=%s, expiresAt=%s, revoked=%s}".formatted(getId(), expiresAt, revokedAt != null);
    }
}
