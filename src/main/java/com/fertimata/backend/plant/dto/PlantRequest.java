package com.fertimata.backend.plant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlantRequest(
        @Size(max = 128) @Schema(example = "Melon pojok timur", description = "Free text the farmer types. Name or code is required.") String name,
        @Size(max = 64)
                @Pattern(regexp = "[A-Za-z0-9._-]+")
                @Schema(example = "Melon-pojok-timur", description = "Optional; made from the name when empty")
                String code,
        @Size(max = 255) @Schema(example = "Dekat pintu masuk") String description) {}
