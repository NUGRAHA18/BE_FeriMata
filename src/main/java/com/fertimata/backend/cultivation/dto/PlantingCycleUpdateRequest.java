package com.fertimata.backend.cultivation.dto;

import com.fertimata.backend.cultivation.CycleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Semua field opsional; hanya yang diisi yang diubah. */
@Schema(name = "PlantingCycleUpdateRequest")
public record PlantingCycleUpdateRequest(
        @Size(max = 128) String name,
        @Size(max = 128) String variety,
        LocalDate transplantDate,
        LocalDate expectedHarvestDate,
        @Size(max = 1000) String notes,
        @Schema(description = "ACTIVE activates this cycle (any other active cycle is ENDED); ENDED closes it")
                CycleStatus status) {}
