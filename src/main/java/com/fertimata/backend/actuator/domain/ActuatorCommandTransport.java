package com.fertimata.backend.actuator.domain;

/**
 * Outbound port for delivering a command to the device that owns the actuator.
 *
 * <p>Declared by the domain and implemented by the messaging layer, which is what keeps
 * {@code ActuatorCommandService} free of topics, payload shapes and broker clients. Swapping MQTT
 * for something else means writing a new implementation of this interface and changing nothing else.
 */
public interface ActuatorCommandTransport {

    /**
     * Hands the command to the transport.
     *
     * @throws com.fertimata.backend.common.exception.MessagePublishException when delivery to the
     *     messaging infrastructure fails. Success means the broker accepted the message - never that
     *     the hardware acted on it.
     */
    void send(OutboundCommand command);

    /** Whether the transport currently believes it can deliver. */
    boolean isAvailable();

    /** Name of the active transport, surfaced on the dashboard. */
    String describe();
}
