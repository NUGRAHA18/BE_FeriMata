package com.fertimata.backend.telemetry.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** SQL PostgreSQL ringkasan per jam dan grafik; native karena JPQL tak punya slot waktu/upsert, dan agregasi harus di DB. */
@Component
public class SensorHistoryQueries {

    private final JdbcClient jdbc;

    public SensorHistoryQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record BucketRow(Instant bucketStart, long count, BigDecimal min, BigDecimal avg, BigDecimal max) {}

    /**
     * Menghitung ulang jam yang menerima pembacaan sejak {@code receivedSince} (received_at: buffer edge membawa recorded_at
     * lama); idempoten. Jam sebelum {@code notBefore} tidak disentuh agar ringkasan utuh tak ditimpa data terpotong retensi.
     */
    public int rollUpReceivedSince(Instant receivedSince, Instant notBefore) {
        return jdbc.sql(
                        """
                        WITH touched AS (
                            SELECT DISTINCT sensor_id,
                                   date_trunc('hour', recorded_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS bucket_start
                            FROM sensor_reading
                            WHERE received_at >= :since
                              AND recorded_at >= :notBefore
                              AND numeric_value IS NOT NULL
                        )
                        INSERT INTO sensor_reading_hourly
                            (sensor_id, bucket_start, sample_count, value_sum, value_min, value_max, updated_at)
                        SELECT t.sensor_id, t.bucket_start, count(*), sum(r.numeric_value),
                               min(r.numeric_value), max(r.numeric_value), now()
                        FROM touched t
                        JOIN sensor_reading r
                          ON r.sensor_id = t.sensor_id
                         AND r.recorded_at >= t.bucket_start
                         AND r.recorded_at < t.bucket_start + interval '1 hour'
                        WHERE r.numeric_value IS NOT NULL
                        GROUP BY t.sensor_id, t.bucket_start
                        ON CONFLICT (sensor_id, bucket_start) DO UPDATE
                        SET sample_count = EXCLUDED.sample_count,
                            value_sum = EXCLUDED.value_sum,
                            value_min = EXCLUDED.value_min,
                            value_max = EXCLUDED.value_max,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("since", utc(receivedSince))
                .param("notBefore", utc(notBefore))
                .update();
    }

    /**
     * Titik grafik satu sensor: sebelum {@code hourlyUntil} dari ringkasan, sisanya mentah; rata-rata dari jumlah/cacah.
     * {@code offsetSeconds} = offset zona agar titik harian mulai 00:00 waktu kebun, bukan UTC.
     */
    public List<BucketRow> buckets(
            long sensorId, Instant from, Instant to, Instant hourlyUntil, long stepSeconds, long offsetSeconds) {
        Instant rawFrom = hourlyUntil.isAfter(from) ? hourlyUntil : from;
        return jdbc.sql(
                        """
                        WITH parts AS (
                            SELECT bucket_start AS t, sample_count::bigint AS n, value_sum AS s,
                                   value_min AS lo, value_max AS hi
                            FROM sensor_reading_hourly
                            WHERE sensor_id = :sensor AND bucket_start >= :from AND bucket_start < :hourlyUntil
                            UNION ALL
                            SELECT recorded_at, 1, numeric_value, numeric_value, numeric_value
                            FROM sensor_reading
                            WHERE sensor_id = :sensor
                              AND numeric_value IS NOT NULL
                              AND recorded_at >= :rawFrom AND recorded_at <= :to
                        )
                        SELECT to_timestamp(floor((extract(epoch FROM t) + :offset) / :step) * :step - :offset) AS bucket,
                               sum(n) AS n,
                               round(sum(s) / sum(n), 6) AS avg_value,
                               min(lo) AS min_value,
                               max(hi) AS max_value
                        FROM parts
                        GROUP BY bucket
                        ORDER BY bucket
                        """)
                .param("sensor", sensorId)
                .param("from", utc(from))
                .param("to", utc(to))
                .param("hourlyUntil", utc(hourlyUntil))
                .param("rawFrom", utc(rawFrom))
                .param("step", stepSeconds)
                .param("offset", offsetSeconds)
                .query((rs, n) -> new BucketRow(
                        rs.getObject("bucket", OffsetDateTime.class).toInstant(),
                        rs.getLong("n"),
                        rs.getBigDecimal("min_value"),
                        rs.getBigDecimal("avg_value"),
                        rs.getBigDecimal("max_value")))
                .list();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
