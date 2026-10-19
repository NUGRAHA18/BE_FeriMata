package com.fertimata.backend.alert;

/** Which part of the system raised an alert. */
public enum AlertSource {
    /** Raised by the backend itself, for example device liveness monitoring. */
    SYSTEM,
    /** Reported by an edge device. */
    DEVICE,
    /** Raised by an automation rule. No rules exist yet; reserved for phase 2. */
    AUTOMATION,
    /** Raised manually by an operator. */
    OPERATOR
}
