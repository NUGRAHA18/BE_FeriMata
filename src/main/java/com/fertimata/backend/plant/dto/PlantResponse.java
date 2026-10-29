package com.fertimata.backend.plant.dto;

import com.fertimata.backend.plant.Plant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "Plant")
public record PlantResponse(
        Long id,
        @Schema(example = "Melon-pojok-timur") String code,
        @Schema(example = "Melon pojok timur") String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static PlantResponse from(Plant plant) {
        return new PlantResponse(
                plant.getId(),
                plant.getCode(),
                plant.getName(),
                plant.getDescription(),
                plant.isActive(),
                plant.getCreatedAt(),
                plant.getUpdatedAt());
    }
}
