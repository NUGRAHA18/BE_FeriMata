package com.fertimata.backend.irrigation;

import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.irrigation.dto.IrrigationRunResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/irrigation/runs")
@Tag(name = "Irrigation schedule")
@SecurityRequirement(name = "bearerAuth")
public class IrrigationRunController {

    private final IrrigationScheduleService service;

    public IrrigationRunController(IrrigationScheduleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Scheduled watering history, newest first",
            description = "One row per watering time per day, including skipped, failed and missed sessions; with the "
                    + "device status of the pump command.")
    public PageResponse<IrrigationRunResponse> runs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return service.runs(PageRequest.of(page, size));
    }
}
