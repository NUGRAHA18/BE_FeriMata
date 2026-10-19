package com.fertimata.backend.alert;

/**
 * Filter opsional untuk daftar alert. Setiap field {@code null} berarti "tidak disaring".
 *
 * @param acknowledged hanya alert yang sudah / belum ditangani
 * @param severity hanya alert dengan tingkat tertentu
 * @param deviceId hanya alert yang terkait perangkat ini
 * @param sensorId hanya alert yang terkait sensor ini
 * @param actuatorId hanya alert yang terkait aktuator ini
 */
public record AlertFilter(Boolean acknowledged, AlertSeverity severity, Long deviceId, Long sensorId, Long actuatorId) {

    public static AlertFilter none() {
        return new AlertFilter(null, null, null, null, null);
    }
}
