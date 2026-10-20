package com.fertimata.backend.plant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(name = "PlantRequest")
public record PlantRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") @Schema(example = "B1-N-02") String code,
        @Size(max = 128) @Schema(description = "Defaults to the code") String name,
        @Size(max = 255) String description,
        @Schema(description = "Cycle id; defaults to the active cycle") Long cycleId,
        @Size(max = 16) @Schema(example = "N-", description = "K0, N-, P-, K-; null for an ordinary production plant") String treatmentCode,
        @Size(max = 16) @Schema(example = "B1") String blockCode,
        @Size(max = 16) @Schema(example = "R1") String rowCode,
        @Size(max = 8) @Schema(example = "A", description = "Soil sensor that represents this plant: A or B") String plotCode,
        @Size(max = 32) @Schema(example = "ST-03") String stationCode,
        @Min(0) Integer positionInRow,
        @Size(max = 16) @Schema(example = "FERTIMATA", description = "FERTIMATA, SIMONKORI or NONE") String comparisonGroup,
        @Size(max = 64) @Schema(example = "1,3,5") String taggedLeaves,
        Map<String, Object> metadata) {}
