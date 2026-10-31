package com.fertimata.backend.dev;

import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.telemetry.history.SensorHistoryQueries;
import com.fertimata.backend.telemetry.retention.TelemetryRetentionProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleBinaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEVELOPMENT ONLY. Mengisi riwayat sensor satu musim: hari lama hanya di {@code sensor_reading_hourly}, beberapa hari
 * terakhir mentah tiap 5 menit. Tidak menimpa data yang ada; deterministik (seed tetap).
 */
@Component
@Profile("dev")
public class DevSeasonSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevSeasonSeeder.class);

    private static final Duration RAW_STEP = Duration.ofMinutes(5);
    private static final int SAMPLES_PER_HOUR = 360;

    private final JdbcTemplate jdbc;
    private final SensorHistoryQueries historyQueries;
    private final TelemetryRetentionProperties retention;
    private final CultivationService cultivationService;

    public DevSeasonSeeder(
            JdbcTemplate jdbc,
            SensorHistoryQueries historyQueries,
            TelemetryRetentionProperties retention,
            CultivationService cultivationService) {
        this.jdbc = jdbc;
        this.historyQueries = historyQueries;
        this.retention = retention;
        this.cultivationService = cultivationService;
    }

    public record Result(int sensors, long hourlyRows, long rawReadings) {}

    @Transactional
    public Result seed(int days) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant start = now.minus(Duration.ofDays(days)).truncatedTo(ChronoUnit.HOURS);
        // Data mentah hanya untuk rentang yang tidak akan langsung dihapus retensi berikutnya.
        long rawDays = retention.enabled() ? Math.max(1, Math.min(6, retention.readingsMaxAge().toDays() - 1)) : 6;
        Instant rawStart = now.minus(Duration.ofDays(rawDays)).truncatedTo(ChronoUnit.HOURS);

        ZoneId zone = cultivationService.zone();
        List<SensorRow> sensors = seededSensors();
        long hourly = 0;
        long raw = 0;
        for (SensorRow sensor : sensors) {
            MetricPattern profile = PROFILES.get(sensor.metricKey());
            hourly += seedHourly(sensor, profile, start, rawStart, zone);
            raw += seedRaw(sensor, profile, start, rawStart, now, zone);
        }
        // Jam di rentang mentah diringkas oleh query yang sama dengan job ringkasan.
        hourly += historyQueries.rollUpReceivedSince(rawStart, rawStart);

        log.info("[DEV] Sensor history seeded: {} sensor(s), {} hourly row(s), {} raw reading(s)", sensors.size(), hourly, raw);
        return new Result(sensors.size(), hourly, raw);
    }

    // ---------------------------------------------------------------- sensor history

    private record SensorRow(long id, long deviceId, String metricKey, String unit) {}

    private List<SensorRow> seededSensors() {
        return jdbc
                .query(
                        "select id, device_id, metric_key, unit from sensor where enabled order by id",
                        (rs, n) -> new SensorRow(
                                rs.getLong("id"), rs.getLong("device_id"), rs.getString("metric_key"), rs.getString("unit")))
                .stream()
                .filter(s -> PROFILES.containsKey(s.metricKey()))
                .toList();
    }

    /** Jam-jam lama langsung sebagai ringkasan; ON CONFLICT menjaga ringkasan yang sudah ada. */
    private long seedHourly(SensorRow sensor, MetricPattern profile, Instant start, Instant rawStart, ZoneId zone) {
        Random random = new Random(sensor.metricKey().hashCode());
        List<Object[]> rows = new ArrayList<>();
        for (Instant hour = start; hour.isBefore(rawStart); hour = hour.plus(Duration.ofHours(1))) {
            double avg = profile.value(start, hour.plus(Duration.ofMinutes(30)), zone, random);
            double spread = profile.hourlySpread() * (0.6 + random.nextDouble() * 0.8);
            rows.add(new Object[] {
                sensor.id(),
                Timestamp.from(hour),
                SAMPLES_PER_HOUR,
                scale(avg * SAMPLES_PER_HOUR),
                scale(Math.max(profile.floor(), avg - spread)),
                scale(avg + spread)
            });
        }
        int[][] counts = jdbc.batchUpdate(
                """
                INSERT INTO sensor_reading_hourly
                    (sensor_id, bucket_start, sample_count, value_sum, value_min, value_max, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (sensor_id, bucket_start) DO NOTHING
                """,
                rows,
                1000,
                (ps, row) -> {
                    for (int i = 0; i < row.length; i++) {
                        ps.setObject(i + 1, row[i]);
                    }
                });
        return sum(counts);
    }

    /** Pembacaan mentah tiap 5 menit sampai pembacaan tertua yang sudah ada (mis. dari simulator). */
    private long seedRaw(SensorRow sensor, MetricPattern profile, Instant start, Instant rawStart, Instant now, ZoneId zone) {
        Timestamp earliest = jdbc.queryForObject(
                "select min(recorded_at) from sensor_reading where sensor_id = ? and recorded_at >= ?",
                Timestamp.class,
                sensor.id(),
                Timestamp.from(rawStart));
        Instant until = earliest == null ? now : earliest.toInstant();
        Random random = new Random(31L * sensor.metricKey().hashCode());
        List<Object[]> rows = new ArrayList<>();
        for (Instant at = rawStart; at.isBefore(until); at = at.plus(RAW_STEP)) {
            rows.add(new Object[] {
                sensor.id(),
                sensor.deviceId(),
                sensor.metricKey(),
                scale(profile.value(start, at, zone, random)),
                sensor.unit(),
                Timestamp.from(at),
                Timestamp.from(at.plusSeconds(1))
            });
        }
        int[][] counts = jdbc.batchUpdate(
                """
                INSERT INTO sensor_reading
                    (sensor_id, device_id, metric_key, numeric_value, unit, recorded_at, received_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                """,
                rows,
                1000,
                (ps, row) -> {
                    for (int i = 0; i < row.length; i++) {
                        ps.setObject(i + 1, row[i]);
                    }
                });
        return sum(counts);
    }

    /** Pola satu metrik: shape(hari sejak tanam, jam lokal), noise per sampel, setengah rentang per jam, nilai minimum. */
    private record MetricPattern(DoubleBinaryOperator shape, double noise, double hourlySpread, double floor) {

        double value(Instant seasonStart, Instant at, ZoneId zone, Random random) {
            double day = Duration.between(seasonStart, at).toMinutes() / 1440.0;
            var local = at.atZone(zone);
            double hour = local.getHour() + local.getMinute() / 60.0;
            return Math.max(floor, shape.applyAsDouble(day, hour) + random.nextGaussian() * noise);
        }
    }

    private static double diurnal(double hour, double peakHour) {
        return Math.cos(2 * Math.PI * (hour - peakHour) / 24);
    }

    private static double daylight(double hour) {
        return hour < 6 || hour > 18 ? 0 : Math.sin(Math.PI * (hour - 6) / 12);
    }

    /** EC larutan naik bertahap mengikuti fase, seperti resep nutrisi melon. */
    private static double solutionEc(double day) {
        return day < 14 ? 1.2 : day < 30 ? 1.8 : day < 50 ? 2.4 : 2.2;
    }

    private static final Map<String, MetricPattern> PROFILES = Map.ofEntries(
            Map.entry("air.temperature", new MetricPattern((d, h) -> 28 + 5 * diurnal(h, 14), 0.4, 1.2, 15)),
            Map.entry("air.humidity", new MetricPattern((d, h) -> 74 - 14 * diurnal(h, 14), 1.5, 4, 30)),
            Map.entry("light.illuminance", new MetricPattern((d, h) -> 52000 * daylight(h) * (0.8 + 0.2 * Math.sin(d)), 1500, 6000, 0)),
            Map.entry("soil_a.moisture", new MetricPattern((d, h) -> 44 + 7 * Math.cos(2 * Math.PI * (d % 2) / 2) - 3 * daylight(h), 0.8, 1.5, 10)),
            Map.entry("soil_a.temperature", new MetricPattern((d, h) -> 26 + 2.2 * diurnal(h, 15), 0.2, 0.5, 15)),
            // mS/cm sejak V12 (satuan fuzzy AI).
            Map.entry("soil_a.ec", new MetricPattern((d, h) -> 0.9 + 0.009 * Math.min(d, 60), 0.025, 0.04, 0.1)),
            Map.entry("soil_a.ph", new MetricPattern((d, h) -> 6.4 + 0.15 * Math.sin(2 * Math.PI * d / 9), 0.03, 0.05, 4)),
            Map.entry("soil_a.nitrogen", new MetricPattern((d, h) -> 140 - 0.6 * d + 10 * Math.sin(d / 3), 3, 4, 10)),
            Map.entry("soil_a.phosphorus", new MetricPattern((d, h) -> 48 + 0.2 * d, 1.5, 2, 5)),
            Map.entry("soil_a.potassium", new MetricPattern((d, h) -> 170 + 1.1 * Math.min(d, 60), 4, 5, 10)),
            Map.entry("tank.ec", new MetricPattern((d, h) -> solutionEc(d) - 0.15 * ((d % 3) / 3), 0.03, 0.06, 0)),
            Map.entry("tank.ph", new MetricPattern((d, h) -> 6.0 + 0.25 * ((d % 3) / 3), 0.03, 0.05, 4)),
            Map.entry("tank.water_temperature", new MetricPattern((d, h) -> 26.5 + 1.5 * diurnal(h, 16), 0.15, 0.3, 15)),
            Map.entry("mains.voltage", new MetricPattern((d, h) -> 221 - 4 * diurnal(h, 19), 1.2, 3, 180)),
            Map.entry("mains.frequency", new MetricPattern((d, h) -> 50, 0.03, 0.05, 49)),
            Map.entry("mains.current", new MetricPattern((d, h) -> 0.9 + 1.2 * daylight(h), 0.1, 0.4, 0)),
            Map.entry("mains.power", new MetricPattern((d, h) -> 190 + 240 * daylight(h), 15, 60, 0)),
            Map.entry("mains.power_factor", new MetricPattern((d, h) -> 0.91, 0.01, 0.02, 0.5)),
            // Kumulatif: ±0,3 kWh per jam.
            Map.entry("mains.energy", new MetricPattern((d, h) -> 120 + d * 24 * 0.3, 0, 0.15, 0)),
            Map.entry("jetson.soc_temperature", new MetricPattern((d, h) -> 47 + 4 * diurnal(h, 14), 0.8, 2, 20)));

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private static long sum(int[][] counts) {
        long total = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                total += Math.max(c, 0);
            }
        }
        return total;
    }
}
