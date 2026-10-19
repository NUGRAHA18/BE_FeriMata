package com.fertimata.backend.websocket;

/**
 * Events the backend pushes to the PWA.
 *
 * <p>Each type owns a topic so a client can subscribe to only what a given screen needs. Every event
 * is additionally mirrored on {@link #FIREHOSE_DESTINATION} for a dashboard that wants all of them.
 * Adding a type is additive: existing clients keep working.
 */
public enum RealtimeEventType {
    SENSOR_READING_UPDATED("/topic/telemetry"),
    DEVICE_STATUS_CHANGED("/topic/devices"),
    ACTUATOR_STATUS_CHANGED("/topic/actuators"),
    ACTUATOR_COMMAND_UPDATED("/topic/actuators/commands"),
    ALERT_CREATED("/topic/alerts"),
    /** Alert yang sudah ada berubah, saat ini hanya karena di-acknowledge operator. */
    ALERT_UPDATED("/topic/alerts"),
    AI_DETECTION_CREATED("/topic/ai");

    public static final String FIREHOSE_DESTINATION = "/topic/events";

    private final String destination;

    RealtimeEventType(String destination) {
        this.destination = destination;
    }

    public String destination() {
        return destination;
    }
}
