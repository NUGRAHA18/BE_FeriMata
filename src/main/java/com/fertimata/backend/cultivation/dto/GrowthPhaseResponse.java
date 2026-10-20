package com.fertimata.backend.cultivation.dto;

import com.fertimata.backend.cultivation.GrowthPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "GrowthPhase")
public record GrowthPhaseResponse(
        Long id,
        String code,
        String name,
        int startHst,
        Integer endHst,
        BigDecimal ecTargetMin,
        BigDecimal ecTargetMax,
        BigDecimal ratioN,
        BigDecimal ratioP,
        BigDecimal ratioK,
        String notes) {

    public static GrowthPhaseResponse from(GrowthPhase phase) {
        return new GrowthPhaseResponse(
                phase.getId(),
                phase.getCode(),
                phase.getName(),
                phase.getStartHst(),
                phase.getEndHst(),
                phase.getEcTargetMin(),
                phase.getEcTargetMax(),
                phase.getRatioN(),
                phase.getRatioP(),
                phase.getRatioK(),
                phase.getNotes());
    }
}
