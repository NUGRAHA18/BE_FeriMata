package com.fertimata.backend.push;

import com.fertimata.backend.common.domain.AuditedEntity;
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

/** Satu browser/HP yang mengizinkan notifikasi push untuk satu akun. */
@Entity
@Table(name = "push_subscription")
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 1024)
    private String endpoint;

    @Column(nullable = false, length = 128)
    private String p256dh;

    @Column(nullable = false, length = 64)
    private String auth;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Override
    public String toString() {
        // Endpoint tidak ditulis utuh: siapa pun yang memegangnya bisa mengirim ke browser itu.
        return "PushSubscription{id=%s, failures=%d}".formatted(getId(), failureCount);
    }
}
