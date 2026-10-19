package com.fertimata.backend.mqtt.mock;

import com.fertimata.backend.mqtt.MqttPublisher;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY. Stands in for the broker when {@code app.mqtt.enabled} is false.
 *
 * <p>Lets the whole backend - REST, persistence, command auditing, WebSocket push - run with no
 * broker and no hardware, which is what unblocks the frontend developer before integration.
 *
 * <p>It keeps the last few published messages in memory so a developer can confirm what the backend
 * would have sent. It is not a broker: nothing is delivered anywhere.
 */
@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoopbackMqttPublisher implements MqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoopbackMqttPublisher.class);
    private static final int HISTORY_LIMIT = 50;

    private final Deque<PublishedMessage> history = new ArrayDeque<>();

    @Override
    public synchronized void publish(String topic, String payload) {
        log.info("[MOCK MQTT] would publish to {}: {}", topic, payload);
        history.addFirst(new PublishedMessage(topic, payload));
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public String transportName() {
        return "MOCK (in-memory loopback)";
    }

    /** Most recent messages first. Used by the development endpoints. */
    public synchronized List<PublishedMessage> recent() {
        return List.copyOf(history);
    }

    public record PublishedMessage(String topic, String payload) {}
}
