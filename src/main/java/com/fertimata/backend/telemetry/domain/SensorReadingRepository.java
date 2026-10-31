package com.fertimata.backend.telemetry.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    Optional<SensorReading> findFirstBySensorIdOrderByRecordedAtDescIdDesc(Long sensorId);

    Page<SensorReading> findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long sensorId, Instant from, Instant to, Pageable pageable);

    /** Latest reading per enabled sensor in one query (dashboard stays one round-trip); disabled/unreviewed sensors hidden. */
    @Query(
            """
            select r from SensorReading r
              join fetch r.sensor s
              join fetch r.device d
            where s.enabled = true
              and r.id in (
                select max(r2.id) from SensorReading r2 group by r2.sensor.id
              )
            order by s.code asc
            """)
    List<SensorReading> findLatestPerSensor();

    long countByReceivedAtAfter(Instant since);

    /** Pembacaan kovariat kalibrasi: metrik lain dari pesan telemetri yang sama (device + recordedAt sama). */
    List<SensorReading> findByDeviceIdAndMetricKeyInAndRecordedAtIn(
            Long deviceId, Collection<String> metricKeys, Collection<Instant> recordedAt);

    /** Pembacaan pertama dan terakhir sebuah metrik dalam rentang; dipakai untuk selisih energi kumulatif. */
    Optional<SensorReading> findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtAsc(
            String metricKey, Instant from, Instant to);

    Optional<SensorReading> findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtDesc(
            String metricKey, Instant from, Instant to);

    /** Semua metrik dari satu pesan telemetri; dipakai sebagai kovariat sampel kalibrasi. */
    List<SensorReading> findByDeviceIdAndRecordedAt(Long deviceId, Instant recordedAt);

    /** Pembacaan terdekat sebelum/pada suatu waktu; dipakai mengisi nilai sensor pada sampel lab. */
    Optional<SensorReading> findFirstBySensorIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(Long sensorId, Instant at);

    Optional<SensorReading> findFirstByMetricKeyAndRecordedAtLessThanEqualOrderByRecordedAtDesc(String metricKey, Instant at);

    /** Menghapus ≤ {@code batchSize} pembacaan sebelum {@code cutoff}; sub-select ber-LIMIT agar DELETE besar tidak mengunci tabel. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    DELETE FROM sensor_reading
                    WHERE id IN (
                        SELECT id FROM sensor_reading
                        WHERE recorded_at < :cutoff
                        ORDER BY id
                        LIMIT :batchSize
                    )
                    """,
            nativeQuery = true)
    int deleteBatchRecordedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
