package com.fertimata.backend.calibration;

import com.fertimata.backend.calibration.dto.CalibrationFitRequest;
import com.fertimata.backend.calibration.dto.CalibrationSampleRequest;
import com.fertimata.backend.calibration.dto.CalibrationSampleResponse;
import com.fertimata.backend.calibration.dto.DriftCheckRequest;
import com.fertimata.backend.calibration.dto.DriftCheckResponse;
import com.fertimata.backend.calibration.dto.SensorCalibrationRequest;
import com.fertimata.backend.calibration.dto.SensorCalibrationResponse;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.security.auth.CurrentUserProvider;
import com.fertimata.backend.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sensors/{sensorId}")
@Tag(name = "Sensor calibration", description = "Lab calibration, paired lab samples and drift checks (Protokol-Data.md §7)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class SensorCalibrationController {

    private final SensorCalibrationService service;
    private final CurrentUserProvider currentUserProvider;

    public SensorCalibrationController(SensorCalibrationService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/calibrations")
    @Operation(summary = "Calibration models of a sensor, newest validFrom first")
    public List<SensorCalibrationResponse> calibrations(@PathVariable Long sensorId) {
        return service.calibrations(sensorId);
    }

    @PostMapping("/calibrations")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Enter a calibration model computed elsewhere",
            description = "calibrated = intercept + slope x raw + Σ coef x covariate. Missing fit statistics are computed from the stored lab samples.")
    public ResponseEntity<SensorCalibrationResponse> create(
            @PathVariable Long sensorId, @Valid @RequestBody SensorCalibrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(sensorId, request, currentUserProvider.username().orElse(null)));
    }

    @PostMapping("/calibrations/fit")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Fit a calibration model from the lab samples",
            description = "Ordinary least squares on raw value plus optional covariates; reports R², RMSE, MAE and Bland-Altman. Previews unless save = true.")
    public SensorCalibrationResponse fit(@PathVariable Long sensorId, @Valid @RequestBody CalibrationFitRequest request) {
        return service.fit(sensorId, request, currentUserProvider.username().orElse(null));
    }

    @GetMapping("/calibration-samples")
    @Operation(summary = "Paired lab samples of a sensor, newest first")
    public List<CalibrationSampleResponse> samples(@PathVariable Long sensorId) {
        return service.samples(sensorId);
    }

    @PostMapping("/calibration-samples")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Record a paired lab sample",
            description = "sensorValue and covariates default to the sensor's telemetry message at sampledAt.")
    public ResponseEntity<CalibrationSampleResponse> addSample(
            @PathVariable Long sensorId, @Valid @RequestBody CalibrationSampleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addSample(sensorId, request, currentUserProvider.username().orElse(null)));
    }

    @DeleteMapping("/calibration-samples/{sampleId}")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(summary = "Delete a wrongly entered lab sample")
    public ResponseEntity<Void> deleteSample(@PathVariable Long sensorId, @PathVariable Long sampleId) {
        service.deleteSample(sensorId, sampleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/drift-checks")
    @Operation(summary = "Drift checks of a sensor, newest first")
    public PageResponse<DriftCheckResponse> driftChecks(
            @PathVariable Long sensorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return service.driftChecks(sensorId, PageRequest.of(page, size));
    }

    @PostMapping("/drift-checks")
    @PreAuthorize("hasRole('" + Role.Names.OPERATOR + "')")
    @Operation(
            summary = "Record a drift check against a standard solution",
            description = "measuredValue defaults to the sensor's latest reading.")
    public ResponseEntity<DriftCheckResponse> addDriftCheck(
            @PathVariable Long sensorId, @Valid @RequestBody DriftCheckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addDriftCheck(sensorId, request, currentUserProvider.username().orElse(null)));
    }
}
