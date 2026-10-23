package com.fertimata.backend.calibration;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorCalibrationRepository extends JpaRepository<SensorCalibration, Long> {

    List<SensorCalibration> findBySensorIdOrderByValidFromDesc(Long sensorId);

    /** Semua kalibrasi untuk sekumpulan sensor; dipakai untuk mengoreksi satu halaman pembacaan sekaligus. */
    List<SensorCalibration> findBySensorIdInOrderByValidFromDesc(Collection<Long> sensorIds);

    Optional<SensorCalibration> findFirstBySensorIdAndValidFromLessThanEqualOrderByValidFromDesc(
            Long sensorId, Instant at);
}
