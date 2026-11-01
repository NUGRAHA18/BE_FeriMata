package com.fertimata.backend.alert;

/** Filter opsional daftar alert; field {@code null} berarti tidak disaring. */
public record AlertFilter(Boolean acknowledged, AlertSeverity severity, Long deviceId, Long sensorId, Long actuatorId) {

    public static AlertFilter none() {
        return new AlertFilter(null, null, null, null, null);
    }
}
