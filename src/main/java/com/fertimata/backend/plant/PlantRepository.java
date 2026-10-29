package com.fertimata.backend.plant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantRepository extends JpaRepository<Plant, Long> {

    /** Deteksi AI dari MQTT boleh menyebut kode tanaman (kontrak edge §4.5). */
    Optional<Plant> findByCode(String code);

    boolean existsByCode(String code);

    List<Plant> findAllByOrderByNameAsc();

    List<Plant> findByActiveOrderByNameAsc(boolean active);

    long countByActiveTrue();

    /** Deteksi AI tetap disimpan saat tanamannya dihapus; FK-nya tidak ber-cascade, jadi dilepas dulu. */
    @Modifying(flushAutomatically = true)
    @Query("update AiDetection d set d.plant = null where d.plant.id = :plantId")
    int detachDetections(@Param("plantId") Long plantId);
}
