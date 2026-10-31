package com.fertimata.backend.websocket;

/** Events pushed to the PWA, one topic per type, each also mirrored on {@link #FIREHOSE_DESTINATION}.
 * Adding a type is additive; existing clients keep working. */
public enum RealtimeEventType {
    SENSOR_READING_UPDATED("/topic/telemetry"),
    DEVICE_STATUS_CHANGED("/topic/devices"),
    ACTUATOR_STATUS_CHANGED("/topic/actuators"),
    ACTUATOR_COMMAND_UPDATED("/topic/actuators/commands"),
    /** Data master aktuator berubah (bukan state laporan perangkat); payload = {@code GET /api/actuators/{id}}. */
    ACTUATOR_UPDATED("/topic/actuators"),
    /** Data master sensor berubah (termasuk auto-register dari telemetri); payload = {@code GET /api/sensors/{id}}. */
    SENSOR_UPDATED("/topic/sensors"),
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
