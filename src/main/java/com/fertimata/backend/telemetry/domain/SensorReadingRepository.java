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

    /**
     * Latest reading of every enabled sensor, used by the dashboard.
     *
     * <p>Written as a subquery rather than N queries: the dashboard must stay a single round-trip
     * regardless of how many sensors the hardware team ends up installing. Disabled sensors are
     * excluded so that an auto-registered, not-yet-reviewed metric does not surface on the dashboard
     * under a placeholder name - its readings are still stored.
     */
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

    /**
     * Menghapus paling banyak {@code batchSize} pembacaan yang lebih tua dari {@code cutoff}.
     *
     * <p>Native SQL dengan sub-select ber-LIMIT: JPQL tidak punya LIMIT pada DELETE, dan menghapus
     * jutaan baris dalam satu pernyataan akan mengunci tabel selama berdetik-detik.
     */
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
