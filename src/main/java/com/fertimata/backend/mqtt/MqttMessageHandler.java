package com.fertimata.backend.mqtt;

/** Handles one inbound kind by translating the payload into a service call; no business logic here. */
public interface MqttMessageHandler {

    TopicKind handles();

    /** {@code deviceCode} comes from the topic, not the payload. */
    void handle(String deviceCode, String payload);
}
