package com.fertimata.backend.cultivation;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantingCycleRepository extends JpaRepository<PlantingCycle, Long> {

    @EntityGraph(attributePaths = "phases")
    Optional<PlantingCycle> findFirstByStatus(CycleStatus status);

    boolean existsByCode(String code);
}
