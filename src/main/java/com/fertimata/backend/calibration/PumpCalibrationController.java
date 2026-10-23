package com.fertimata.backend.calibration;

import com.fertimata.backend.calibration.dto.DispensedVolumeResponse;
import com.fertimata.backend.calibration.dto.PumpCalibrationRequest;
import com.fertimata.backend.calibration.dto.PumpCalibrationResponse;
import com.fertimata.backend.security.auth.CurrentUserProvider;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actuators/{actuatorId}")
@Tag(name = "Pump calibration", description = "Peristaltic pump flow and dispensed volume (Protokol-Data.md §6, §8)")
@SecurityRequirement(name = "bearerAuth")
public class PumpCalibrationController {

    private final PumpCalibrationService service;
    private final CurrentUserProvider currentUserProvider;

    public PumpCalibrationController(PumpCalibrationService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/calibrations")
    @Operation(summary = "Flow calibrations of a pump, newest first")
    public List<PumpCalibrationResponse> calibrations(@PathVariable Long actuatorId) {
        return service.calibrations(actuatorId);
    }

    @PostMapping("/calibrations")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Record a flow calibration",
            description = "Send the trials (at least 3) and the flow is total volume / total time; or send flowMlPerSecond directly.")
    public ResponseEntity<PumpCalibrationResponse> create(
            @PathVariable Long actuatorId, @Valid @RequestBody PumpCalibrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(actuatorId, request, currentUserProvider.username().orElse(null)));
    }

    @GetMapping("/dispensed")
    @Operation(
            summary = "Volume dispensed by a pump",
            description = "Σ run time of EXECUTED activating commands x the flow calibrated before each run. Defaults to the last 24 hours.")
    public DispensedVolumeResponse dispensed(
            @PathVariable Long actuatorId,
            @Parameter(description = "Inclusive range start, ISO-8601 instant")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @Parameter(description = "Inclusive range end, ISO-8601 instant")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to) {
        return service.dispensed(actuatorId, from, to);
    }
}
