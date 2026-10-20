package com.fertimata.backend.cultivation;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Satu musim tanam.
 *
 * <p>Semua yang bergantung pada umur tanaman (HST, fase pertumbuhan, target EC, rasio N:P:K)
 * diturunkan dari {@link #transplantDate}. Fase-fasenya adalah data milik siklus ini, bukan
 * konstanta: batas hari dan angka agronominya boleh berbeda antar musim.
 */
@Entity
@Table(name = "planting_cycle")
@Getter
@Setter
@NoArgsConstructor
public class PlantingCycle extends AuditedEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 128)
    private String variety;

    @Column(name = "transplant_date", nullable = false)
    private LocalDate transplantDate;

    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    @Column(name = "ended_at")
    private LocalDate endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CycleStatus status = CycleStatus.ACTIVE;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startHst asc")
    private List<GrowthPhase> phases = new ArrayList<>();

    public PlantingCycle(String code, String name, LocalDate transplantDate) {
        this.code = code;
        this.name = name;
        this.transplantDate = transplantDate;
    }

    /** Hari setelah transplant; 0 pada hari transplant, negatif bila siklus masih PLANNED. */
    public long hstOn(LocalDate date) {
        return ChronoUnit.DAYS.between(transplantDate, date);
    }

    /** Fase yang berlaku pada HST tertentu, bila ada. */
    public Optional<GrowthPhase> phaseAt(long hst) {
        return phases.stream().filter(p -> p.covers(hst)).findFirst();
    }

    /** Fase pertama yang dimulai setelah HST tertentu, bila ada. */
    public Optional<GrowthPhase> nextPhaseAfter(long hst) {
        return phases.stream().filter(p -> p.getStartHst() > hst).findFirst();
    }

    public void replacePhases(List<GrowthPhase> replacement) {
        phases.clear();
        for (GrowthPhase phase : replacement) {
            phase.setCycle(this);
            phases.add(phase);
        }
    }
}
