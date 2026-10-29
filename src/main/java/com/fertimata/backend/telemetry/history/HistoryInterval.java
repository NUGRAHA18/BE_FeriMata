package com.fertimata.backend.telemetry.history;

import com.fertimata.backend.common.exception.BusinessRuleException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lebar titik grafik yang boleh diminta.
 *
 * <p>Daftar tetap, bukan durasi bebas: setiap lebar mulai 1 jam adalah kelipatan jam, sehingga titik
 * di luar masa retensi bisa disusun tepat dari {@code sensor_reading_hourly}. Lebar di bawah 1 jam
 * hanya bisa dihitung dari pembacaan mentah.
 */
public enum HistoryInterval {
    MINUTE_1("1m", Duration.ofMinutes(1)),
    MINUTE_5("5m", Duration.ofMinutes(5)),
    MINUTE_15("15m", Duration.ofMinutes(15)),
    MINUTE_30("30m", Duration.ofMinutes(30)),
    HOUR_1("1h", Duration.ofHours(1)),
    HOUR_3("3h", Duration.ofHours(3)),
    HOUR_6("6h", Duration.ofHours(6)),
    HOUR_12("12h", Duration.ofHours(12)),
    DAY_1("1d", Duration.ofDays(1)),
    WEEK_1("7d", Duration.ofDays(7));

    private static final Map<String, HistoryInterval> BY_LABEL =
            Arrays.stream(values()).collect(Collectors.toMap(HistoryInterval::label, i -> i));

    private final String label;
    private final Duration duration;

    HistoryInterval(String label, Duration duration) {
        this.label = label;
        this.duration = duration;
    }

    public String label() {
        return label;
    }

    public Duration duration() {
        return duration;
    }

    public long seconds() {
        return duration.toSeconds();
    }

    /** Bisa disusun dari ringkasan per jam. */
    public boolean coveredByHourly() {
        return duration.compareTo(Duration.ofHours(1)) >= 0;
    }

    public static HistoryInterval fromLabel(String label) {
        HistoryInterval interval = BY_LABEL.get(label);
        if (interval == null) {
            throw new BusinessRuleException(
                    "Unknown interval %s; use one of %s".formatted(label, labels()), Map.of("interval", label));
        }
        return interval;
    }

    public static String labels() {
        return Arrays.stream(values()).map(HistoryInterval::label).collect(Collectors.joining(", "));
    }
}
