package com.fertimata.backend.plant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    Optional<Plant> findByCode(String code);
}
