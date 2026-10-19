package com.fertimata.backend.actuator.domain;

/**
 * Lifecycle of an actuator command.
 *
 * <p>The distinction between {@link #SENT} and {@link #EXECUTED} is the important one: the backend
 * only knows a command reached the broker, never that hardware acted on it. Only an acknowledgement
 * from the device moves a command to {@link #EXECUTED}.
 */
public enum CommandStatus {
    /** Persisted, not yet handed to the broker. */
    PENDING,
    /** Published to the broker; awaiting device acknowledgement. */
    SENT,
    /** The device confirmed it carried the command out. */
    EXECUTED,
    /** Publishing failed, or the device reported a failure. */
    FAILED,
    /** Withdrawn before it was executed. */
    CANCELLED;

    public boolean isTerminal() {
        return this == EXECUTED || this == FAILED || this == CANCELLED;
    }
}
