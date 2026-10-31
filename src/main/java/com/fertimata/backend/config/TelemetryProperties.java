package com.fertimata.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ingest telemetri: {@code autoRegisterSensors} membuat sensor placeholder disabled alih-alih membuang data;
 * timestamp device lebih maju dari {@code maxClockSkew} diganti waktu server; {@code maxMetricsPerMessage} membatasi payload. */

@ConfigurationProperties(prefix = "app.telemetry")
public record TelemetryProperties(
        boolean autoRegisterSensors, Duration maxClockSkew, int maxMetricsPerMessage) {}
