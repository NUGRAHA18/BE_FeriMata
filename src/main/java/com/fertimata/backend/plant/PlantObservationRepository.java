package com.fertimata.backend.plant;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantObservationRepository extends JpaRepository<PlantObservation, Long> {

    Page<PlantObservation> findByPlantIdOrderByObservedAtDesc(Long plantId, Pageable pageable);

    Optional<PlantObservation> findFirstByPlantIdOrderByObservedAtDesc(Long plantId);
}
