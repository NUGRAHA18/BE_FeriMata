package com.fertimata.backend.device;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceCode(String deviceCode);

    boolean existsByDeviceCode(String deviceCode);

    List<Device> findAllByOrderByDeviceCodeAsc();

    long countByStatus(DeviceStatus status);

    List<Device> findByPowerSourceIsNotNull();

    /** Devices that still claim to be online but have not been heard from since the cut-off. */
    List<Device> findByStatusAndLastSeenAtBefore(DeviceStatus status, Instant cutoff);
}
