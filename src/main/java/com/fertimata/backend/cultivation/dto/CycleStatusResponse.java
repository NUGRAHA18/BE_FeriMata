package com.fertimata.backend.cultivation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Keadaan siklus aktif "hari ini": apa yang perlu diketahui dashboard dan (nanti) fuzzy dosing.
 *
 * @param hst hari setelah transplant; 0 pada hari transplant
 * @param currentPhase null bila HST di luar semua fase yang didefinisikan
 * @param nextPhase fase berikutnya, bila ada
 * @param daysUntilNextPhase null bila tidak ada fase berikutnya
 * @param plantCount semua tanaman aktif di siklus ini
 * @param treatmentPlantCount tanaman dengan kode perlakuan (K0 / N- / P- / K-)
 */
@Schema(name = "CycleStatus")
public record CycleStatusResponse(
        PlantingCycleResponse cycle,
        LocalDate today,
        long hst,
        GrowthPhaseResponse currentPhase,
        GrowthPhaseResponse nextPhase,
        Long daysUntilNextPhase,
        long plantCount,
        long treatmentPlantCount) {}
