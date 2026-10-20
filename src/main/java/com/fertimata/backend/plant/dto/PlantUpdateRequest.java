package com.fertimata.backend.plant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Semua field opsional; hanya yang diisi yang diubah. */
@Schema(name = "PlantUpdateRequest")
public record PlantUpdateRequest(
        @Size(max = 128) String name,
        @Size(max = 255) String description,
        Long cycleId,
        @Size(max = 16) String treatmentCode,
        @Size(max = 16) String blockCode,
        @Size(max = 16) String rowCode,
        @Size(max = 8) String plotCode,
        @Size(max = 32) String stationCode,
        @Min(0) Integer positionInRow,
        @Size(max = 16) String comparisonGroup,
        @Size(max = 64) String taggedLeaves,
        Boolean active,
        Map<String, Object> metadata) {}
