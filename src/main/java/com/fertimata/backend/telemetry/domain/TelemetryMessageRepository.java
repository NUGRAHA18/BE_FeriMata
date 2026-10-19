package com.fertimata.backend.telemetry.domain;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelemetryMessageRepository extends JpaRepository<TelemetryMessage, Long> {

    Page<TelemetryMessage> findByDeviceIdOrderByReceivedAtDesc(Long deviceId, Pageable pageable);

    long countByStatusAndReceivedAtAfter(TelemetryStatus status, Instant since);

    /**
     * Menghapus paling banyak {@code batchSize} payload mentah yang diterima sebelum {@code cutoff}.
     * Pembacaan yang masih merujuk ke pesan tersebut ikut dilepas (ON DELETE SET NULL, lihat V3).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    DELETE FROM telemetry_message
                    WHERE id IN (
                        SELECT id FROM telemetry_message
                        WHERE received_at < :cutoff
                        ORDER BY id
                        LIMIT :batchSize
                    )
                    """,
            nativeQuery = true)
    int deleteBatchReceivedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
