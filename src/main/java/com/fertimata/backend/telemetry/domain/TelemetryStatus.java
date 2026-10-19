package com.fertimata.backend.telemetry.domain;

/** Outcome of ingesting one telemetry message. */
public enum TelemetryStatus {
    /** Every metric in the payload was stored. */
    ACCEPTED,
    /** The message was stored but at least one metric could not be (unknown or disabled sensor). */
    PARTIALLY_ACCEPTED,
    /** Nothing was stored; the envelope is kept so the bad payload can be inspected. */
    REJECTED
}
