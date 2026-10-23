package com.fertimata.backend.calibration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Menghitung model dari sampel lab sensor ini dengan kuadrat terkecil. */
@Schema(name = "CalibrationFitRequest")
public record CalibrationFitRequest(
        @Size(max = 5)
                @Schema(
                        description = "Covariate metric keys, e.g. soil moisture and temperature (Protokol §7 step 4)",
                        example = "[\"soil_a.moisture\", \"soil_a.temperature\"]")
                List<@Size(max = 64) String> covariateKeys,
        @Schema(description = "Only samples taken from this instant on") Instant samplesFrom,
        @Schema(description = "Only samples taken up to this instant") Instant samplesTo,
        @Schema(description = "Store the result as the sensor's calibration; false (default) only previews it") Boolean save,
        @Schema(description = "When saved: readings from this instant on use the model; defaults to now") Instant validFrom,
        @Size(max = 500) String notes) {}
