package com.fertimata.backend.mqtt;

/**
 * The logical channels the backend speaks over the broker.
 *
 * <p>Direction is part of the contract: the backend only ever publishes on {@link #COMMAND} and only
 * ever consumes the rest.
 */
public enum TopicKind {
    /** Device to backend: sensor measurements. */
    TELEMETRY(false),
    /** Device to backend: heartbeat and device/actuator state. */
    STATUS(false),
    /** Backend to device: actuator commands. */
    COMMAND(true),
    /** Device to backend: the outcome of a command it was given. */
    COMMAND_ACK(false),
    /** Device to backend: results from the vision model. */
    AI(false),
    /**
     * Device to backend: a fault the edge agent detected itself - an SSR stuck on, a dry-running
     * pump, a dosing loop that did not converge. The backend stores it; it does not re-derive it.
     */
    ALERT(false);

    private final boolean outbound;

    TopicKind(boolean outbound) {
        this.outbound = outbound;
    }

    public boolean isOutbound() {
        return outbound;
    }

    public boolean isInbound() {
        return !outbound;
    }
}
