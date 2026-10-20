package com.fertimata.backend.plant.dto;

import com.fertimata.backend.plant.PlantObservation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(name = "PlantObservation")
public record PlantObservationResponse(
        Long id,
        Long plantId,
        String plantCode,
        Instant observedAt,
        Integer hst,
        BigDecimal heightCm,
        Integer leafCount,
        BigDecimal leaf3LengthCm,
        BigDecimal leaf3WidthCm,
        String visualNotes,
        @Schema(description = "metricKey -> {value, unit, recordedAt} of the plot's soil sensors when recorded")
                Map<String, Object> soilSnapshot,
        String observedBy,
        Instant createdAt) {

    public static PlantObservationResponse from(PlantObservation o, Map<String, Object> soilSnapshot) {
        return new PlantObservationResponse(
                o.getId(),
                o.getPlant().getId(),
                o.getPlant().getCode(),
                o.getObservedAt(),
                o.getHst(),
                o.getHeightCm(),
                o.getLeafCount(),
                o.getLeaf3LengthCm(),
                o.getLeaf3WidthCm(),
                o.getVisualNotes(),
                soilSnapshot,
                o.getObservedBy() == null ? null : o.getObservedBy().getUsername(),
                o.getCreatedAt());
    }
}
