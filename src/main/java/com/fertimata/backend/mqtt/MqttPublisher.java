package com.fertimata.backend.mqtt;

/** Minimal outbound transport (topic + payload). Business services go through a domain port such as
 * {@code ActuatorCommandTransport}, never building topics or wire payloads themselves. */
public interface MqttPublisher {

    /** Throws {@code MessagePublishException} when the message cannot be handed to the broker. */
    void publish(String topic, String payload);

    /** Whether the transport currently believes it can deliver a message. */
    boolean isConnected();

    /** Human-readable name of the active transport, shown on the dashboard. */
    String transportName();
}
