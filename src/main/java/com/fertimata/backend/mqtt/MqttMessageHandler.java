package com.fertimata.backend.mqtt;

/**
 * Handles one kind of inbound message.
 *
 * <p>Implementations translate a wire payload into a call on a business service. They must not
 * contain business logic themselves - that is what keeps the messaging layer replaceable.
 */
public interface MqttMessageHandler {

    TopicKind handles();

    /**
     * @param deviceCode device code taken from the topic, not from the payload
     * @param payload raw message body
     */
    void handle(String deviceCode, String payload);
}
