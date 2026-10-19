package com.fertimata.backend.actuator.domain;

/** Who asked for an actuator command. Answers the operator question "was this manual or automatic?" */
public enum CommandSource {
    /** A signed-in operator pressed a control in the PWA. */
    OPERATOR,
    /** An automation rule fired. No rules are implemented yet; the value exists for phase 2. */
    AUTOMATION,
    /** The backend itself issued the command (safety stop, reconciliation, ...). */
    SYSTEM
}
