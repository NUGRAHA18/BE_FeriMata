package com.fertimata.backend.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The topic convention is not final, so the one class that owns topic strings is worth testing
 * carefully - especially the case where one template is a prefix of another.
 */
class MqttTopicResolverTest {

    private static final Map<TopicKind, String> TEMPLATES = new EnumMap<>(Map.of(
            TopicKind.TELEMETRY, "fertimata/{deviceId}/telemetry",
            TopicKind.STATUS, "fertimata/{deviceId}/status",
            TopicKind.COMMAND, "fertimata/{deviceId}/command",
            TopicKind.COMMAND_ACK, "fertimata/{deviceId}/command/ack",
            TopicKind.AI, "fertimata/{deviceId}/ai"));

    private final MqttTopicResolver resolver = new MqttTopicResolver(properties(TEMPLATES));

    @Test
    @DisplayName("a device topic is built from the configured template")
    void buildsTopic() {
        assertThat(resolver.topicFor(TopicKind.COMMAND, "JETSON-001"))
                .isEqualTo("fertimata/JETSON-001/command");
    }

    @Test
    @DisplayName("inbound subscriptions use a wildcard for the device segment and exclude outbound kinds")
    void buildsSubscriptions() {
        assertThat(resolver.inboundSubscriptions())
                .containsExactlyInAnyOrder(
                        "fertimata/+/telemetry",
                        "fertimata/+/status",
                        "fertimata/+/command/ack",
                        "fertimata/+/ai")
                .doesNotContain("fertimata/+/command");
    }

    @Test
    @DisplayName("an inbound topic resolves to its kind and device code")
    void parsesTopic() {
        Optional<MqttTopicResolver.ResolvedTopic> resolved = resolver.parse("fertimata/JETSON-001/telemetry");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().kind()).isEqualTo(TopicKind.TELEMETRY);
        assertThat(resolved.get().deviceCode()).isEqualTo("JETSON-001");
    }

    @Test
    @DisplayName("a command acknowledgement is not mistaken for a command, even though one template is a prefix")
    void prefersTheMoreSpecificTemplate() {
        Optional<MqttTopicResolver.ResolvedTopic> resolved = resolver.parse("fertimata/JETSON-001/command/ack");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().kind()).isEqualTo(TopicKind.COMMAND_ACK);
    }

    @Test
    @DisplayName("an outbound-only topic is not accepted as inbound")
    void doesNotParseOutboundTopics() {
        assertThat(resolver.parse("fertimata/JETSON-001/command")).isEmpty();
    }

    @Test
    @DisplayName("an unrecognised topic resolves to nothing rather than being guessed at")
    void ignoresUnknownTopic() {
        assertThat(resolver.parse("something/else/entirely")).isEmpty();
        assertThat(resolver.parse("")).isEmpty();
        assertThat(resolver.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("changing the convention in configuration changes every topic")
    void honoursADifferentConvention() {
        Map<TopicKind, String> other = new EnumMap<>(TopicKind.class);
        other.put(TopicKind.TELEMETRY, "farm/v2/{deviceId}/measurements");
        MqttTopicResolver custom = new MqttTopicResolver(properties(other));

        assertThat(custom.topicFor(TopicKind.TELEMETRY, "EDGE-9")).isEqualTo("farm/v2/EDGE-9/measurements");
        assertThat(custom.parse("farm/v2/EDGE-9/measurements")).isPresent();
    }

    @Test
    @DisplayName("asking for a topic that was never configured fails loudly instead of inventing one")
    void missingTemplateFails() {
        MqttTopicResolver empty = new MqttTopicResolver(properties(new EnumMap<>(TopicKind.class)));

        assertThatThrownBy(() -> empty.topicFor(TopicKind.COMMAND, "JETSON-001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mqtt.topics");
    }

    private static MqttProperties properties(Map<TopicKind, String> topics) {
        return new MqttProperties(
                false, "tcp://localhost:1883", "test-client", null, null, 1, null, null, null, true, true, topics);
    }
}
