package com.fertimata.backend.push;

import com.fertimata.backend.alert.AlertSeverity;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Web Push untuk peringatan (default mati). Kunci VAPID dibuat sekali ({@code npx web-push generate-vapid-keys}); menggantinya
 * memutus semua langganan. {@code allowedEndpointHosts} wajib: backend POST ke URL langganan, tanpa daftar ini rawan SSRF. */
@Validated
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(
        boolean enabled,
        String vapidPublicKey,
        String vapidPrivateKey,
        String vapidSubject,
        @NotNull AlertSeverity minSeverity,
        @NotNull Duration ttl,
        @NotNull Duration requestTimeout,
        @NotNull List<String> allowedEndpointHosts) {}
