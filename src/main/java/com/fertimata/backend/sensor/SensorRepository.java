package com.fertimata.backend.sensor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SensorRepository extends JpaRepository<Sensor, Long> {

    Optional<Sensor> findByDeviceIdAndMetricKey(Long deviceId, String metricKey);

    Optional<Sensor> findByDeviceIdAndCode(Long deviceId, String code);

    List<Sensor> findByDeviceIdOrderByCodeAsc(Long deviceId);

    @Query("select s from Sensor s join fetch s.device order by s.code asc")
    List<Sensor> findAllWithDevice();

    long countByEnabledTrue();
}
