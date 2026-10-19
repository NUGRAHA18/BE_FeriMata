package com.fertimata.backend.websocket;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Realtime endpoint settings.
 *
 * @param endpoint path the PWA opens the STOMP connection on
 * @param allowedOrigins explicit origin list for the handshake; never a wildcard in production
 * @param requireAuthentication when true, a STOMP CONNECT frame must carry a valid access token.
 *     Switching this off is only sensible while a frontend developer is wiring things up locally.
 */
@ConfigurationProperties(prefix = "app.websocket")
public record WebSocketProperties(String endpoint, List<String> allowedOrigins, boolean requireAuthentication) {}
