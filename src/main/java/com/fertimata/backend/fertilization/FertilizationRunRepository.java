package com.fertimata.backend.fertilization;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FertilizationRunRepository extends JpaRepository<FertilizationRun, Long> {

    /** Pemupukan yang masih punya langkah (paling banyak satu, dijaga indeks unik). */
    Optional<FertilizationRun> findFirstByStepIsNotNull();

    Optional<FertilizationRun> findFirstByStatusInOrderByRunDateDescIdDesc(Collection<FertilizationRun.Status> statuses);

    boolean existsByRunDateAndReasonCode(LocalDate runDate, String reasonCode);

    Page<FertilizationRun> findAllByOrderByRunDateDescIdDesc(Pageable pageable);
}
