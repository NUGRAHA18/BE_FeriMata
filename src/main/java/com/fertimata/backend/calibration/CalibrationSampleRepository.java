package com.fertimata.backend.calibration;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalibrationSampleRepository extends JpaRepository<CalibrationSample, Long> {

    List<CalibrationSample> findBySensorIdOrderBySampledAtDesc(Long sensorId);

    List<CalibrationSample> findBySensorIdAndSampledAtBetweenOrderBySampledAtAsc(Long sensorId, Instant from, Instant to);
}
