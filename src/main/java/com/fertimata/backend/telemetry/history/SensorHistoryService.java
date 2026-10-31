package com.fertimata.backend.telemetry.history;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.cultivation.CultivationProperties;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.telemetry.retention.TelemetryRetentionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Min/rata-rata/maks per titik: ≥ 1 jam dari ringkasan (+ mentah untuk jam terakhir), < 1 jam hanya selama retensi. */
@Service
public class SensorHistoryService {

    private final SensorService sensorService;
    private final SensorHistoryQueries queries;
    private final SensorHistoryProperties properties;
    private final TelemetryRetentionProperties retention;
    private final CultivationProperties cultivation;
    private final Clock clock;

    public SensorHistoryService(
            SensorService sensorService,
            SensorHistoryQueries queries,
            SensorHistoryProperties properties,
            TelemetryRetentionProperties retention,
            CultivationProperties cultivation,
            Clock clock) {
        this.sensorService = sensorService;
        this.queries = queries;
        this.properties = properties;
        this.retention = retention;
        this.cultivation = cultivation;
        this.clock = clock;
    }

    /** {@code intervalLabel} null memilih interval terkecil dengan paling banyak {@code target-points} titik. */
    @Transactional(readOnly = true)
    public SensorHistorySeriesResponse series(Long sensorId, Instant from, Instant to, String intervalLabel) {
        Sensor sensor = sensorService.require(sensorId);
        Instant now = clock.instant();
        Instant end = to == null ? now : to;
        Instant start = from == null ? end.minus(properties.defaultRange()) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("The start of the range must not be after its end");
        }

        Optional<Instant> rawHorizon = rawHorizon(now);
        HistoryInterval interval = intervalLabel == null
                ? pick(start, end, rawHorizon)
                : checked(HistoryInterval.fromLabel(intervalLabel), start, end, rawHorizon);

        long offset = cultivation.zone().getRules().getOffset(start).getTotalSeconds();
        if (interval == HistoryInterval.WEEK_1) {
            // Epoch Unix jatuh pada hari Kamis; geser 3 hari supaya minggu mulai Senin.
            offset += Duration.ofDays(3).toSeconds();
        }
        Instant alignedStart = align(start, interval.seconds(), offset);
        Instant hourlyUntil = interval.coveredByHourly() ? hourlyUntil(now) : alignedStart;

        List<SensorHistorySeriesResponse.Point> points =
                queries.buckets(sensor.getId(), alignedStart, end, hourlyUntil, interval.seconds(), offset).stream()
                        .map(row -> new SensorHistorySeriesResponse.Point(
                                row.bucketStart(), row.count(), row.min(), row.avg(), row.max()))
                        .toList();
        return new SensorHistorySeriesResponse(
                sensor.getId(),
                sensor.getCode(),
                sensor.getMetricKey(),
                sensor.getUnit(),
                interval.label(),
                interval.seconds(),
                alignedStart,
                end,
                points);
    }

    /** Pembacaan mentah sebelum ini sudah (atau segera) dihapus retensi. Kosong bila retensi mati. */
    Optional<Instant> rawHorizon(Instant now) {
        return retention.enabled() ? Optional.of(now.minus(retention.readingsMaxAge())) : Optional.empty();
    }

    /** Jam sebelum ini sudah stabil di ringkasan: job hanya menghitung ulang dalam {@code rollup-lookback} terakhir. */
    Instant hourlyUntil(Instant now) {
        return now.minus(properties.rollupLookback()).truncatedTo(ChronoUnit.HOURS);
    }

    private HistoryInterval pick(Instant start, Instant end, Optional<Instant> rawHorizon) {
        long rangeSeconds = Duration.between(start, end).toSeconds();
        for (HistoryInterval candidate : HistoryInterval.values()) {
            if (!available(candidate, start, rawHorizon)) {
                continue;
            }
            if (points(rangeSeconds, candidate) <= properties.targetPoints()) {
                return candidate;
            }
        }
        return HistoryInterval.values()[HistoryInterval.values().length - 1];
    }

    private HistoryInterval checked(
            HistoryInterval interval, Instant start, Instant end, Optional<Instant> rawHorizon) {
        if (!available(interval, start, rawHorizon)) {
            throw new BusinessRuleException(
                    "Intervals below 1h only cover the last %s; use 1h or wider for older data"
                            .formatted(retention.readingsMaxAge().toDays() + " days"),
                    Map.of("interval", interval.label()));
        }
        long points = points(Duration.between(start, end).toSeconds(), interval);
        if (points > properties.maxPoints()) {
            throw new BusinessRuleException(
                    "Interval %s gives %d points for this range; the limit is %d"
                            .formatted(interval.label(), points, properties.maxPoints()),
                    Map.of("interval", interval.label(), "points", points));
        }
        return interval;
    }

    private static boolean available(HistoryInterval interval, Instant start, Optional<Instant> rawHorizon) {
        return interval.coveredByHourly() || rawHorizon.map(h -> !start.isBefore(h)).orElse(true);
    }

    private static long points(long rangeSeconds, HistoryInterval interval) {
        return rangeSeconds / interval.seconds() + 1;
    }

    /** Awal titik yang memuat {@code instant}, dengan titik harian mulai tengah malam zona kebun. */
    static Instant align(Instant instant, long stepSeconds, long offsetSeconds) {
        long shifted = instant.getEpochSecond() + offsetSeconds;
        return Instant.ofEpochSecond(Math.floorDiv(shifted, stepSeconds) * stepSeconds - offsetSeconds);
    }
}
