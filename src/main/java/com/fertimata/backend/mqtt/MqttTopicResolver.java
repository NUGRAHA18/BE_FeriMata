package com.fertimata.backend.mqtt;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Builds and parses broker topics from the configured templates.
 *
 * <p>Every topic string in the system passes through here. That is what makes the "topic convention
 * is not final" problem a one-file problem.
 */
@Component
public class MqttTopicResolver {

    private final Map<TopicKind, String> templates = new EnumMap<>(TopicKind.class);

    public MqttTopicResolver(MqttProperties properties) {
        if (properties.topics() != null) {
            properties.topics().forEach((kind, template) -> {
                if (template != null && !template.isBlank()) {
                    templates.put(kind, template.trim());
                }
            });
        }
    }

    /** Concrete topic for one device, for example {@code fertimata/JETSON-001/command}. */
    public String topicFor(TopicKind kind, String deviceCode) {
        String template = requireTemplate(kind);
        return template.replace(MqttProperties.DEVICE_PLACEHOLDER, deviceCode);
    }

    /** Wildcard subscription covering every device for one kind of inbound message. */
    public String subscriptionFor(TopicKind kind) {
        return requireTemplate(kind).replace(MqttProperties.DEVICE_PLACEHOLDER, "+");
    }

    /** Subscriptions for every inbound kind that has a configured template. */
    public List<String> inboundSubscriptions() {
        return templates.keySet().stream()
                .filter(TopicKind::isInbound)
                .map(this::subscriptionFor)
                .sorted()
                .toList();
    }

    public boolean hasTemplate(TopicKind kind) {
        return templates.containsKey(kind);
    }

    /**
     * Works out which kind of message arrived and which device sent it.
     *
     * <p>Templates are matched from the most specific to the least specific, so a
     * {@code .../command/ack} topic is not mistaken for a {@code .../command} topic when one
     * template is a prefix of another.
     */
    public Optional<ResolvedTopic> parse(String topic) {
        if (topic == null || topic.isBlank()) {
            return Optional.empty();
        }
        String[] segments = topic.split("/", -1);
        return templates.entrySet().stream()
                .filter(entry -> entry.getKey().isInbound())
                .sorted((a, b) -> Integer.compare(b.getValue().length(), a.getValue().length()))
                .map(entry -> match(entry.getKey(), entry.getValue(), segments))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<ResolvedTopic> match(TopicKind kind, String template, String[] segments) {
        String[] templateSegments = template.split("/", -1);
        if (templateSegments.length != segments.length) {
            return Optional.empty();
        }
        String deviceCode = null;
        for (int i = 0; i < templateSegments.length; i++) {
            if (MqttProperties.DEVICE_PLACEHOLDER.equals(templateSegments[i])) {
                deviceCode = segments[i];
            } else if (!templateSegments[i].equals(segments[i])) {
                return Optional.empty();
            }
        }
        if (deviceCode == null || deviceCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedTopic(kind, deviceCode));
    }

    private String requireTemplate(TopicKind kind) {
        String template = templates.get(kind);
        if (template == null) {
            throw new IllegalStateException(
                    "No MQTT topic template configured for " + kind + " (app.mqtt.topics." + kind + ")");
        }
        return template;
    }

    /** What an inbound topic turned out to be. */
    public record ResolvedTopic(TopicKind kind, String deviceCode) {}
}
