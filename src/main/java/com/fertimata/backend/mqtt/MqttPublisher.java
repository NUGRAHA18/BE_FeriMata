package com.fertimata.backend.mqtt;

/**
 * Outbound transport towards the broker.
 *
 * <p>This is the only place the rest of the backend is allowed to reach the messaging layer, and it
 * is deliberately tiny: a topic and a payload. Business services do not call it directly either -
 * they go through a domain port such as
 * {@code com.fertimata.backend.actuator.domain.ActuatorCommandTransport}, so they never build a
 * topic or a wire payload themselves.
 */
public interface MqttPublisher {

    /**
     * Publishes a payload.
     *
     * @throws com.fertimata.backend.common.exception.MessagePublishException when the message could
     *     not be handed to the broker
     */
    void publish(String topic, String payload);

    /** Whether the transport currently believes it can deliver a message. */
    boolean isConnected();

    /** Human-readable name of the active transport, shown on the dashboard. */
    String transportName();
}
