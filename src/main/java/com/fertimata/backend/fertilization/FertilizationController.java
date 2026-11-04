package com.fertimata.backend.fertilization;

import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.fertilization.dto.FertilizationDoseRequest;
import com.fertimata.backend.fertilization.dto.FertilizationDoseResponse;
import com.fertimata.backend.fertilization.dto.FertilizationIntervalRequest;
import com.fertimata.backend.fertilization.dto.FertilizationRunResponse;
import com.fertimata.backend.fertilization.dto.FertilizationScheduleResponse;
import com.fertimata.backend.security.auth.CurrentUserProvider;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fertilization")
@Tag(
        name = "Fertilization",
        description = "Automatic fertilization: the interval comes from the AI service; the N/P/K dose from the AI by "
                + "default, or the farmer's millilitres. The backend runs it before the first watering session of a due day")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class FertilizationController {

    private final FertilizationService service;
    private final FertilizationDoseService doses;
    private final CurrentUserProvider currentUserProvider;

    public FertilizationController(
            FertilizationService service, FertilizationDoseService doses, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.doses = doses;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/schedule")
    @Operation(
            summary = "Fertilization interval (from the AI service), last and next fertilization",
            description = "Never fails because of the AI service: aiReachable=false with aiError instead.")
    public FertilizationScheduleResponse schedule() {
        return service.schedule();
    }

    @PutMapping("/schedule")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Set 'fertilize every N days'",
            description = "Stored in the AI service (its dosing schedule), creating it when missing. 502 when the AI "
                    + "service cannot be used.")
    public FertilizationScheduleResponse setInterval(@Valid @RequestBody FertilizationIntervalRequest request) {
        return service.setInterval(request.intervalDays());
    }

    @DeleteMapping("/schedule")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Stop automatic fertilization",
            description = "Deletes the dosing schedule in the AI service. 502 when the AI service cannot be used.")
    public FertilizationScheduleResponse deleteInterval() {
        return service.deleteInterval();
    }

    @GetMapping("/dose")
    @Operation(
            summary = "How the N/P/K dose is decided",
            description = "AI (default) or MANUAL millilitres, plus each dosing pump's calibration and most ml per run.")
    public FertilizationDoseResponse dose() {
        return doses.dose();
    }

    @PutMapping("/dose")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Use the AI dose or the farmer's millilitres",
            description = "MANUAL needs N, P and K ml (0 allowed, not all 0). Every pump with ml > 0 must be enabled and "
                    + "flow-calibrated, and the ml must fit in one run (maxMl). 409 details.reason: DOSE_MISSING, "
                    + "DOSE_ZERO, PUMP_UNAVAILABLE, PUMP_NOT_CALIBRATED, TOO_MUCH (with nutrient, actuatorCode, maxMl). "
                    + "Stale EC/pH in the drum still blocks dosing in both modes.")
    public FertilizationDoseResponse setDose(@Valid @RequestBody FertilizationDoseRequest request) {
        return doses.update(request, currentUserProvider.username().orElse(null));
    }

    @GetMapping("/runs")
    @Operation(summary = "Fertilization runs, newest first", description = "Including skipped and failed attempts.")
    public PageResponse<FertilizationRunResponse> runs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return service.runs(PageRequest.of(page, size));
    }
}
