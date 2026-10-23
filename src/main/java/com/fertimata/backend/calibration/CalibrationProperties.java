package com.fertimata.backend.calibration;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pengaturan kalibrasi (Protokol-Data.md §7-§8).
 *
 * @param readingMaxAge umur maksimum pembacaan sensor yang boleh dipakai mengisi otomatis nilai sensor
 *     pada sampel lab, uji drift, atau tegangan bus pada kalibrasi pompa
 * @param voltageMetricKey metrik tegangan yang dicatat bersama kalibrasi pompa
 */
@ConfigurationProperties(prefix = "app.calibration")
public record CalibrationProperties(
        Duration readingMaxAge, String voltageMetricKey, DriftCheck driftCheck, Pump pump, Reminders reminders) {

    public CalibrationProperties {
        readingMaxAge = readingMaxAge == null ? Duration.ofMinutes(15) : readingMaxAge;
        voltageMetricKey = voltageMetricKey == null || voltageMetricKey.isBlank() ? "mains.voltage" : voltageMetricKey;
        driftCheck = driftCheck == null ? new DriftCheck(null, null) : driftCheck;
        pump = pump == null ? new Pump(null, null, null) : pump;
        reminders = reminders == null ? new Reminders(null, null, null) : reminders;
    }

    /**
     * @param metricKeys sensor yang wajib diuji drift (probe EC/pH)
     * @param maxAge jarak maksimum antar uji; Protokol §7: mingguan
     */
    public record DriftCheck(List<String> metricKeys, Duration maxAge) {
        public DriftCheck {
            metricKeys = metricKeys == null ? List.of() : List.copyOf(metricKeys);
            maxAge = maxAge == null ? Duration.ofDays(7) : maxAge;
        }
    }

    /**
     * @param types tipe aktuator yang debitnya dikalibrasi (pompa peristaltik)
     * @param maxAge jarak maksimum antar kalibrasi; Protokol §8: bulanan
     * @param minTrials ulangan minimum bila ulangan dicatat satu per satu; Protokol §8: 3
     */
    public record Pump(List<String> types, Duration maxAge, Integer minTrials) {
        public Pump {
            types = types == null
                    ? List.of("DOSING_PUMP")
                    : types.stream().map(t -> t.trim().toUpperCase(Locale.ROOT)).toList();
            maxAge = maxAge == null ? Duration.ofDays(30) : maxAge;
            minTrials = minTrials == null ? 3 : minTrials;
        }

        public boolean covers(String actuatorType) {
            return actuatorType != null && types.contains(actuatorType.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** Jadwal {@link CalibrationReminderJob}; dibaca langsung oleh anotasi {@code @Scheduled}. */
    public record Reminders(Boolean enabled, Duration interval, Duration initialDelay) {
        public Reminders {
            enabled = enabled == null || enabled;
            interval = interval == null ? Duration.ofHours(6) : interval;
            initialDelay = initialDelay == null ? Duration.ofMinutes(5) : initialDelay;
        }
    }
}
