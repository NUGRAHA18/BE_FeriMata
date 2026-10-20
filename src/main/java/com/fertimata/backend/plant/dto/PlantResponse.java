package com.fertimata.backend.plant.dto;

import com.fertimata.backend.plant.Plant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(name = "Plant")
public record PlantResponse(
        Long id,
        String code,
        String name,
        String description,
        Long cycleId,
        String cycleCode,
        String treatmentCode,
        String blockCode,
        String rowCode,
        String plotCode,
        String stationCode,
        Integer positionInRow,
        String comparisonGroup,
        String taggedLeaves,
        boolean active,
        Map<String, Object> metadata,
        @Schema(description = "Most recent observation, null when none has been recorded") PlantObservationResponse lastObservation,
        Instant createdAt,
        Instant updatedAt) {

    public static PlantResponse from(Plant plant, Map<String, Object> metadata, PlantObservationResponse lastObservation) {
        return new PlantResponse(
                plant.getId(),
                plant.getCode(),
                plant.getName(),
                plant.getDescription(),
                plant.getCycle() == null ? null : plant.getCycle().getId(),
                plant.getCycle() == null ? null : plant.getCycle().getCode(),
                plant.getTreatmentCode(),
                plant.getBlockCode(),
                plant.getRowCode(),
                plant.getPlotCode(),
                plant.getStationCode(),
                plant.getPositionInRow(),
                plant.getComparisonGroup(),
                plant.getTaggedLeaves(),
                plant.isActive(),
                metadata,
                lastObservation,
                plant.getCreatedAt(),
                plant.getUpdatedAt());
    }
}
