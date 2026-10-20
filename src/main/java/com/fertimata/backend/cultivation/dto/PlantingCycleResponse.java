package com.fertimata.backend.cultivation.dto;

import com.fertimata.backend.cultivation.CycleStatus;
import com.fertimata.backend.cultivation.PlantingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "PlantingCycle")
public record PlantingCycleResponse(
        Long id,
        String code,
        String name,
        String variety,
        LocalDate transplantDate,
        LocalDate expectedHarvestDate,
        LocalDate endedAt,
        CycleStatus status,
        String notes,
        List<GrowthPhaseResponse> phases,
        Instant createdAt,
        Instant updatedAt) {

    public static PlantingCycleResponse from(PlantingCycle cycle) {
        return new PlantingCycleResponse(
                cycle.getId(),
                cycle.getCode(),
                cycle.getName(),
                cycle.getVariety(),
                cycle.getTransplantDate(),
                cycle.getExpectedHarvestDate(),
                cycle.getEndedAt(),
                cycle.getStatus(),
                cycle.getNotes(),
                cycle.getPhases().stream().map(GrowthPhaseResponse::from).toList(),
                cycle.getCreatedAt(),
                cycle.getUpdatedAt());
    }
}
