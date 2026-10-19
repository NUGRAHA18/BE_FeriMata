package com.fertimata.backend.actuator.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActuatorRepository extends JpaRepository<Actuator, Long> {

    Optional<Actuator> findByDeviceIdAndCode(Long deviceId, String code);

    List<Actuator> findByDeviceIdOrderByCodeAsc(Long deviceId);

    @Query("select a from Actuator a join fetch a.device order by a.code asc")
    List<Actuator> findAllWithDevice();

    /**
     * Loads an actuator together with its device.
     *
     * <p>Command handling reads the device code and status after the lookup transaction has closed,
     * so the association must already be initialised.
     */
    @Query("select a from Actuator a join fetch a.device where a.id = :id")
    Optional<Actuator> findByIdWithDevice(Long id);
}
