package com.fertimata.backend.fertilization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FertilizationDoseSettingRepository extends JpaRepository<FertilizationDoseSetting, Long> {

    /** Satu-satunya baris (dijaga indeks unik); kosong = mode AI. */
    Optional<FertilizationDoseSetting> findFirstByOrderByIdAsc();
}
