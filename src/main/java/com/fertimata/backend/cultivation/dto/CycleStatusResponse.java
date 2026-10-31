package com.fertimata.backend.cultivation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Siklus aktif hari ini (HST 0 = hari semai); field fase null bila HST di luar fase atau tidak ada fase berikutnya. */
@Schema(name = "CycleStatus")
public record CycleStatusResponse(
        PlantingCycleResponse cycle,
        LocalDate today,
        long hst,
        GrowthPhaseResponse currentPhase,
        GrowthPhaseResponse nextPhase,
        Long daysUntilNextPhase,
        long plantCount) {}
