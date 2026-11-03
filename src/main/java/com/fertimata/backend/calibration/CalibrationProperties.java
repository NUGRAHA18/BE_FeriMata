package com.fertimata.backend.calibration;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pengaturan kalibrasi (Protokol-Data.md §7-§8). {@code readingMaxAge}: umur maksimum pembacaan untuk mengisi otomatis
 * sampel lab, uji drift, atau tegangan bus kalibrasi pompa.
 */
@ConfigurationProperties(prefix = "app.calibration")
public record CalibrationProperties(
        Duration readingMaxAge, String voltageMetricKey, DriftCheck driftCheck, Pump pump, Reminders reminders) {

    public CalibrationProperties {
        readingMaxAge = readingMaxAge == null ? Duration.ofMinutes(15) : readingMaxAge;
        voltageMetricKey = voltageMetricKey == null || voltageMetricKey.isBlank() ? "mains.voltage" : voltageMetricKey;
        driftCheck = driftCheck == null ? new DriftCheck(null, null) : driftCheck;
        pump = pump == null ? new Pump(null, null, null, null) : pump;
        reminders = reminders == null ? new Reminders(null, null, null) : reminders;
    }

    /**
     * Sensor yang diberi pengingat uji drift (probe EC/pH); {@code maxAge} antar uji, Protokol §7: mingguan. Kosong =
     * tanpa pengingat (bawaan sejak 3 Nov 2026: kalibrasi sensor tidak dipakai petani).
     */
    public record DriftCheck(List<String> metricKeys, Duration maxAge) {
        public DriftCheck {
            metricKeys = metricKeys == null ? List.of() : List.copyOf(metricKeys);
            maxAge = maxAge == null ? Duration.ofDays(7) : maxAge;
        }
    }

    /**
     * Tipe pompa yang dikalibrasi; {@code maxAge} Protokol §8: bulanan; {@code minTrials} Protokol §8: 3 ulangan;
     * {@code reminderSnooze}: pengingat yang sudah ditandai ditangani tidak dimunculkan lagi selama ini.
     */
    public record Pump(List<String> types, Duration maxAge, Integer minTrials, Duration reminderSnooze) {
        public Pump {
            reminderSnooze = reminderSnooze == null ? Duration.ofDays(7) : reminderSnooze;
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
