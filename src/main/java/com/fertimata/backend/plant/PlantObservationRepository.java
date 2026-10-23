package com.fertimata.backend.plant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantObservationRepository extends JpaRepository<PlantObservation, Long> {

    Page<PlantObservation> findByPlantIdOrderByObservedAtDesc(Long plantId, Pageable pageable);

    Optional<PlantObservation> findFirstByPlantIdOrderByObservedAtDesc(Long plantId);

    /** Semua pengamatan (opsional satu siklus) beserta tanamannya, untuk ekspor laporan. */
    @Query(
            """
            select o from PlantObservation o
              join fetch o.plant p
              left join fetch o.observedBy
            where (:cycleId is null or p.cycle.id = :cycleId)
            order by p.code asc, o.observedAt asc
            """)
    List<PlantObservation> findAllForExport(@Param("cycleId") Long cycleId);
}
