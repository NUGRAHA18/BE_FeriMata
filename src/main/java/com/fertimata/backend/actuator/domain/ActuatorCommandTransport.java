package com.fertimata.backend.actuator.domain;

/** Outbound port owned by the domain, keeping the command service free of topics, payloads and broker
 * clients; replacing MQTT only needs a new implementation. */
public interface ActuatorCommandTransport {

    /** Hands the command over; success means the broker accepted it, never that hardware acted.
     * Throws {@code MessagePublishException} when delivery to the messaging infrastructure fails. */
    void send(OutboundCommand command);

    /** Whether the transport currently believes it can deliver. */
    boolean isAvailable();

    /** Name of the active transport, surfaced on the dashboard. */
    String describe();
}
