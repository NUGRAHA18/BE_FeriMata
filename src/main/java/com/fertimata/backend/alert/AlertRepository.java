package com.fertimata.backend.alert;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    long countByAcknowledgedFalse();

    long countByAcknowledgedFalseAndSeverity(AlertSeverity severity);

    /** Used to avoid raising the same infrastructure alert again while it is still unacknowledged. */
    boolean existsByTypeAndRelatedDeviceIdAndAcknowledgedFalse(String type, Long deviceId);

    @Query("select a from Alert a where a.acknowledged = false order by a.severity desc, a.createdAt desc")
    List<Alert> findActive(Pageable pageable);
}
