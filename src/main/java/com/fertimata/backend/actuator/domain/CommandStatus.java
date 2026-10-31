package com.fertimata.backend.actuator.domain;

/** Command lifecycle. SENT only means the broker got it; only a device ACK moves it to {@link #EXECUTED}. */
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
