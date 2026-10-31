package com.fertimata.backend.telemetry.history;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Riwayat teragregasi untuk grafik; nilai mentah tanpa kalibrasi. Titik tanpa pembacaan tidak dikirim (celah = alat mati). */
@Schema(name = "SensorHistorySeries")
public record SensorHistorySeriesResponse(
        Long sensorId,
        @Schema(example = "SENSOR-TEMP-01") String sensorCode,
        @Schema(example = "temperature") String metricKey,
        @Schema(example = "C") String unit,
        @Schema(description = "Width of every point", example = "1h") String interval,
        @Schema(example = "3600") long intervalSeconds,
        @Schema(description = "Start of the first point, aligned to the interval in the farm time zone") Instant from,
        Instant to,
        List<Point> points) {

    @Schema(name = "SensorHistoryPoint")
    public record Point(
            @Schema(description = "Start of this point; it covers [bucketStart, bucketStart + interval)")
                    Instant bucketStart,
            @Schema(description = "Number of raw readings behind this point", example = "360") long count,
            @Schema(example = "26.1") BigDecimal min,
            @Schema(example = "28.4") BigDecimal avg,
            @Schema(example = "31.0") BigDecimal max) {}
}
