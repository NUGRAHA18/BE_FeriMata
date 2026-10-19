package com.fertimata.backend.telemetry.domain;

/** Where a telemetry message entered the backend. Kept for audit and for filtering out test data. */
public enum TelemetrySource {
    /** Received from the message broker - the only source in production. */
    MQTT,
    /** Injected through the development-only REST endpoint. */
    DEV_API,
    /** Produced by the development telemetry simulator. */
    SIMULATOR
}
