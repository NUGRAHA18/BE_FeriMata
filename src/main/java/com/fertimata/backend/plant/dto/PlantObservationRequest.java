package com.fertimata.backend.plant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Satu pengamatan manual. Semua ukuran opsional: yang kosong berarti tidak diukur hari itu. */
@Schema(name = "PlantObservationRequest")
public record PlantObservationRequest(
        @Schema(description = "Defaults to now") Instant observedAt,
        @DecimalMin("0") @Schema(example = "48.5") BigDecimal heightCm,
        @Min(0) @Schema(example = "11") Integer leafCount,
        @DecimalMin("0") @Schema(example = "14.2") BigDecimal leaf3LengthCm,
        @DecimalMin("0") @Schema(example = "12.8") BigDecimal leaf3WidthCm,
        @Size(max = 1000) @Schema(example = "Daun ke-3 mulai menguning merata dari tepi") String visualNotes,
        @Schema(description = "Attach the latest soil readings of the plant's plot (default true)") Boolean snapshotSoil) {}
