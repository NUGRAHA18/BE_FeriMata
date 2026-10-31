package com.fertimata.backend.mqtt;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Builds and parses topics from the configured templates; every topic string passes through here. */
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

    /** Resolves kind and device from a topic, matching the most specific template first so
     * {@code .../command/ack} is not mistaken for {@code .../command}. */
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
