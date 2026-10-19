package com.fertimata.backend.mqtt;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Broker connection and topic configuration.
 *
 * <p><b>Topic names are provisional.</b> The naming convention is not locked, so every topic is a
 * template configured here and nowhere else - no topic string is written in Java code. When the
 * hardware team settles the convention, this file changes and nothing else does.
 *
 * <p>Credentials come from the environment and are never logged or exposed through the API.
 *
 * @param enabled when false no broker connection is attempted and the in-memory loopback transport
 *     is used instead, which is what lets the backend run with no hardware and no broker present
 * @param topics templates per {@link TopicKind}; {@code {deviceId}} is substituted with a device code
 */
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
