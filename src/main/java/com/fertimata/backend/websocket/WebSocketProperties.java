package com.fertimata.backend.websocket;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Realtime endpoint; {@code allowedOrigins} never a wildcard in production. Turn off
 * {@code requireAuthentication} only for local frontend wiring. */
@ConfigurationProperties(prefix = "app.websocket")
public record WebSocketProperties(String endpoint, List<String> allowedOrigins, boolean requireAuthentication) {}
