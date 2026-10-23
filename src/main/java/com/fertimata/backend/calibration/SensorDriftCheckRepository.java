package com.fertimata.backend.calibration;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorDriftCheckRepository extends JpaRepository<SensorDriftCheck, Long> {

    Page<SensorDriftCheck> findBySensorIdOrderByCheckedAtDesc(Long sensorId, Pageable pageable);

    Optional<SensorDriftCheck> findFirstBySensorIdOrderByCheckedAtDesc(Long sensorId);
}
