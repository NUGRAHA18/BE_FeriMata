package com.fertimata.backend.mqtt;

/** Logical broker channels; the backend only publishes on {@link #COMMAND} and only consumes the rest. */
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
    /** Device to backend: a fault the edge detected itself (SSR stuck on, dry-running pump); stored, not re-derived. */
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
