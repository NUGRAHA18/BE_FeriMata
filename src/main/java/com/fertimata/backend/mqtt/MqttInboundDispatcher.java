package com.fertimata.backend.mqtt;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Single entry point for everything arriving from the broker.
 *
 * <p>Its whole job is routing: work out what a topic means, find the handler, and make sure one bad
 * message cannot take down the subscriber. Nothing here knows what a sensor or an actuator is.
 */
@Component
public class MqttInboundDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MqttInboundDispatcher.class);

    private final MqttTopicResolver topicResolver;
    private final Map<TopicKind, MqttMessageHandler> handlers = new EnumMap<>(TopicKind.class);

    public MqttInboundDispatcher(MqttTopicResolver topicResolver, List<MqttMessageHandler> handlers) {
        this.topicResolver = topicResolver;
        handlers.forEach(handler -> this.handlers.put(handler.handles(), handler));
        log.info("MQTT inbound dispatcher ready for {}", this.handlers.keySet());
    }

    public void dispatch(String topic, String payload) {
        topicResolver
                .parse(topic)
                .ifPresentOrElse(
                        resolved -> route(resolved, topic, payload),
                        () -> log.warn("Ignoring message on unrecognised topic {}", topic));
    }

    private void route(MqttTopicResolver.ResolvedTopic resolved, String topic, String payload) {
        MqttMessageHandler handler = handlers.get(resolved.kind());
        if (handler == null) {
            log.warn("No handler registered for {} messages (topic {})", resolved.kind(), topic);
            return;
        }
        try {
            handler.handle(resolved.deviceCode(), payload);
        } catch (RuntimeException ex) {
            // An unprocessable message must not kill the subscriber or stall the broker connection.
            log.error("Failed to process {} message from {}: {}", resolved.kind(), resolved.deviceCode(),
                    ex.getMessage(), ex);
        }
    }
}
