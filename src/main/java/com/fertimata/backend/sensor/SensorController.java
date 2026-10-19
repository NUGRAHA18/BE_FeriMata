package com.fertimata.backend.sensor;

import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sensors")
@Tag(name = "Sensors", description = "Measurement channels and their readings")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @GetMapping
    @Operation(summary = "List sensors", description = "Optionally filtered to a single device.")
    public List<SensorResponse> list(@RequestParam(required = false) Long deviceId) {
        return deviceId == null ? sensorService.list() : sensorService.listByDevice(deviceId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one sensor")
    public SensorResponse get(@PathVariable Long id) {
        return sensorService.get(id);
    }

    @GetMapping("/{id}/latest")
    @Operation(summary = "Get the most recent reading for a sensor")
    public SensorReadingResponse latest(@PathVariable Long id) {
        return sensorService.latest(id);
    }

    @GetMapping("/{id}/history")
    @Operation(
            summary = "Get readings for a sensor",
            description = "Defaults to the last 24 hours when no range is supplied.")
    public PageResponse<SensorReadingResponse> history(
            @PathVariable Long id,
            @Parameter(description = "Inclusive range start, ISO-8601 instant")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @Parameter(description = "Inclusive range end, ISO-8601 instant")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int size) {
        return sensorService.history(id, from, to, PageRequest.of(page, size));
    }

    @PostMapping
    @Operation(summary = "Register a sensor")
    public ResponseEntity<SensorResponse> register(@Valid @RequestBody SensorRegistrationRequest request) {
        SensorResponse created = sensorService.register(request);
        return ResponseEntity.created(URI.create("/api/sensors/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Update a sensor",
            description = "Used to name and enable a sensor that was auto-registered from telemetry.")
    public SensorResponse update(@PathVariable Long id, @Valid @RequestBody SensorUpdateRequest request) {
        return sensorService.update(id, request);
    }
}
