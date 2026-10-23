package com.fertimata.backend.calibration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActuatorCalibrationRepository extends JpaRepository<ActuatorCalibration, Long> {

    List<ActuatorCalibration> findByActuatorIdOrderByCalibratedAtDesc(Long actuatorId);

    Optional<ActuatorCalibration> findFirstByActuatorIdOrderByCalibratedAtDesc(Long actuatorId);
}
