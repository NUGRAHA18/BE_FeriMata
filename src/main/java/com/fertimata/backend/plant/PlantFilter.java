package com.fertimata.backend.plant;

/** Filter opsional daftar tanaman; {@code null} berarti tidak disaring. */
public record PlantFilter(
        Long cycleId, String treatmentCode, String blockCode, String stationCode, String comparisonGroup, Boolean active) {}
