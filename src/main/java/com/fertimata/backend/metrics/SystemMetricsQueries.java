package com.fertimata.backend.metrics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Query agregat PostgreSQL untuk metrik sistem.
 *
 * <p>SQL langsung, bukan JPQL: percentile_cont dan pengelompokan per slot waktu tidak ada di JPQL, dan
 * memuat ratusan ribu pembacaan ke memori Jetson hanya untuk dihitung tidak bisa diterima. Satu pesan
 * telemetri = satu pasangan (device, recorded_at) di sensor_reading, karena semua metriknya berbagi
 * cap waktu yang sama.
 */
@Component
public class SystemMetricsQueries {

    private final JdbcClient jdbc;

    public SystemMetricsQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record DeviceTelemetryRow(
            long deviceId, long messages, long liveBuckets, Double latencyP50, Double latencyP95, Double latencyMax) {}

    public record DailyRow(long deviceId, LocalDate day, long messages) {}

    public record CommandRow(long actuatorId, String actuatorCode, String actuatorType, String status, long count) {}

    public List<DeviceTelemetryRow> telemetryPerDevice(Instant from, Instant to, long bucketSeconds) {
        return jdbc.sql(
                        """
                        SELECT device_id,
                               count(*) AS messages,
                               count(DISTINCT floor(extract(epoch FROM recorded_at) / :bucket)) AS live_buckets,
                               percentile_cont(0.5) WITHIN GROUP (ORDER BY latency) AS p50,
                               percentile_cont(0.95) WITHIN GROUP (ORDER BY latency) AS p95,
                               max(latency) AS max_latency
                        FROM (
                            SELECT DISTINCT device_id, recorded_at, received_at,
                                   extract(epoch FROM (received_at - recorded_at)) AS latency
                            FROM sensor_reading
                            WHERE recorded_at BETWEEN :from AND :to
                        ) m
                        GROUP BY device_id
                        """)
                .param("bucket", bucketSeconds)
                .param("from", utc(from))
                .param("to", utc(to))
                .query((rs, n) -> new DeviceTelemetryRow(
                        rs.getLong("device_id"),
                        rs.getLong("messages"),
                        rs.getLong("live_buckets"),
                        nullableDouble(rs.getObject("p50")),
                        nullableDouble(rs.getObject("p95")),
                        nullableDouble(rs.getObject("max_latency"))))
                .list();
    }

    public List<DailyRow> messagesPerDay(Instant from, Instant to, ZoneId zone) {
        return jdbc.sql(
                        """
                        SELECT device_id, (recorded_at AT TIME ZONE :zone)::date AS day, count(*) AS messages
                        FROM (
                            SELECT DISTINCT device_id, recorded_at
                            FROM sensor_reading
                            WHERE recorded_at BETWEEN :from AND :to
                        ) m
                        GROUP BY device_id, day
                        ORDER BY device_id, day
                        """)
                .param("zone", zone.getId())
                .param("from", utc(from))
                .param("to", utc(to))
                .query((rs, n) -> new DailyRow(
                        rs.getLong("device_id"), rs.getObject("day", LocalDate.class), rs.getLong("messages")))
                .list();
    }

    public List<CommandRow> commandsPerActuator(Instant from, Instant to) {
        return jdbc.sql(
                        """
                        SELECT a.id AS actuator_id, a.code, a.type, c.status, count(*) AS n
                        FROM actuator_command c
                        JOIN actuator a ON a.id = c.actuator_id
                        WHERE c.requested_at BETWEEN :from AND :to
                        GROUP BY a.id, a.code, a.type, c.status
                        ORDER BY a.code, c.status
                        """)
                .param("from", utc(from))
                .param("to", utc(to))
                .query((rs, n) -> new CommandRow(
                        rs.getLong("actuator_id"),
                        rs.getString("code"),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getLong("n")))
                .list();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Double nullableDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
