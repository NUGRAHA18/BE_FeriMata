package com.fertimata.backend.plant.dto;

import com.fertimata.backend.plant.HarvestRecord;
import com.fertimata.backend.plant.Plant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "Harvest")
public record HarvestResponse(
        Long id,
        Long plantId,
        String plantCode,
        String treatmentCode,
        String blockCode,
        String comparisonGroup,
        LocalDate harvestedOn,
        Integer hst,
        BigDecimal fruitWeightG,
        BigDecimal brix,
        BigDecimal firmness,
        String firmnessUnit,
        BigDecimal fleshThicknessMm,
        BigDecimal dryMatterPercent,
        String notes,
        String recordedBy,
        Instant createdAt) {

    public static HarvestResponse from(HarvestRecord h) {
        Plant plant = h.getPlant();
        return new HarvestResponse(
                h.getId(),
                plant.getId(),
                plant.getCode(),
                plant.getTreatmentCode(),
                plant.getBlockCode(),
                plant.getComparisonGroup(),
                h.getHarvestedOn(),
                h.getHst(),
                h.getFruitWeightG(),
                h.getBrix(),
                h.getFirmness(),
                h.getFirmnessUnit(),
                h.getFleshThicknessMm(),
                h.getDryMatterPercent(),
                h.getNotes(),
                h.getRecordedBy() == null ? null : h.getRecordedBy().getUsername(),
                h.getCreatedAt());
    }
}
