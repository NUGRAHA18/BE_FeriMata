package com.fertimata.backend.actuator;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Batas tunggu ACK alat (edge menjawab segera, patroli saat mulai, kontrak MQTT §4.3); lewat timeout → NO_RESPONSE.
 * Perintah macet lebih lama dari {@code alertWindow} ditutup tanpa peringatan agar tidak membanjiri. */
@Validated
@ConfigurationProperties(prefix = "app.actuator.response")
public record CommandResponseProperties(
        @NotNull Duration timeout, @NotNull Duration checkInterval, @NotNull Duration alertWindow) {}
