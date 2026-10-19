package com.fertimata.backend.ai;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiDetectionRepository extends JpaRepository<AiDetection, Long> {

    @Query("select d from AiDetection d join fetch d.device order by d.detectedAt desc")
    Page<AiDetection> findRecent(Pageable pageable);

    Page<AiDetection> findByDeviceIdOrderByDetectedAtDesc(Long deviceId, Pageable pageable);

    Page<AiDetection> findByDetectionTypeOrderByDetectedAtDesc(String detectionType, Pageable pageable);

    Page<AiDetection> findByStationCodeOrderByDetectedAtDesc(String stationCode, Pageable pageable);

    Optional<AiDetection> findFirstByOrderByDetectedAtDescIdDesc();
}
