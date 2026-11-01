package com.fertimata.backend.mqtt;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Broker and topic config. Topic names are provisional, so every topic is a template here, never in Java.
 * {@code enabled=false} uses the in-memory loopback (no broker/hardware); credentials are never logged. */
@ConfigurationProperties(prefix = "app.mqtt")
public record MqttProperties(
        boolean enabled,
        String brokerUrl,
        String clientId,
        String username,
        String password,
        int qos,
        Duration keepAlive,
        Duration connectionTimeout,
        Duration completionTimeout,
        boolean cleanStart,
        boolean automaticReconnect,
        Map<TopicKind, String> topics) {

    /** Placeholder replaced by a device code when a topic is built. */
    public static final String DEVICE_PLACEHOLDER = "{deviceId}";

    /** Never include credentials in diagnostics. */
    @Override
    public String toString() {
        return "MqttProperties{enabled=%s, brokerUrl=%s, clientId=%s, qos=%d}"
                .formatted(enabled, brokerUrl, clientId, qos);
    }
}
