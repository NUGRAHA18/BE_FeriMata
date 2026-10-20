package com.fertimata.backend.plant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlantRepository extends JpaRepository<Plant, Long>, JpaSpecificationExecutor<Plant> {

    Optional<Plant> findByCode(String code);

    boolean existsByCode(String code);

    long countByCycleIdAndActiveTrue(Long cycleId);

    long countByCycleIdAndActiveTrueAndTreatmentCodeIsNotNull(Long cycleId);
}
