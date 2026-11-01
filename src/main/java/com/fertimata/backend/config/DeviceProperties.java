package com.fertimata.backend.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Liveness/registrasi device: tanpa heartbeat selama {@code offlineTimeout} → OFFLINE (device mati tak sempat lapor).
 * TODO(hardware): set beberapa kali interval heartbeat edge. {@code autoRegister} untuk testing, false di produksi. */
@Validated
@ConfigurationProperties(prefix = "app.device")
public record DeviceProperties(
        @NotNull Duration offlineTimeout, @NotNull Duration livenessCheckInterval, boolean autoRegister) {}
