package com.fertimata.backend.fertilization;

import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.fertilization.dto.FertilizationIntervalRequest;
import com.fertimata.backend.fertilization.dto.FertilizationRunResponse;
import com.fertimata.backend.fertilization.dto.FertilizationScheduleResponse;
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
        description = "Automatic fertilization: the interval and the N/P/K doses come from the AI service; the backend "
                + "runs it before the first watering session of a due day")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class FertilizationController {

    private final FertilizationService service;

    public FertilizationController(FertilizationService service) {
        this.service = service;
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

    @GetMapping("/runs")
    @Operation(summary = "Fertilization runs, newest first", description = "Including skipped and failed attempts.")
    public PageResponse<FertilizationRunResponse> runs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return service.runs(PageRequest.of(page, size));
    }
}
