package com.fertimata.backend.sensor;

import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import com.fertimata.backend.telemetry.history.SensorHistorySeriesResponse;
import com.fertimata.backend.telemetry.history.SensorHistoryService;
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
    private final SensorHistoryService sensorHistoryService;

    public SensorController(SensorService sensorService, SensorHistoryService sensorHistoryService) {
        this.sensorService = sensorService;
        this.sensorHistoryService = sensorHistoryService;
    }

    @GetMapping
    @Operation(
            summary = "List sensors",
            description = "Optionally filtered to a single device. Disabled sensors (not installed, retired, or "
                    + "auto-registered and not yet reviewed) are left out unless includeDisabled=true, which the "
                    + "settings screen uses to review and enable them.")
    public List<SensorResponse> list(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        List<SensorResponse> sensors = deviceId == null ? sensorService.list() : sensorService.listByDevice(deviceId);
        return includeDisabled ? sensors : sensors.stream().filter(SensorResponse::enabled).toList();
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

    @GetMapping("/{id}/history/aggregate")
    @Operation(
            summary = "Get min/avg/max of a sensor per time interval",
            description =
                    """
                    For long-range charts. Defaults to the last 7 days. Points are aligned to the farm time                     zone (a 1d point starts at local midnight, 7d on Monday) and empty intervals are omitted.                     Intervals of 1h and wider also cover data older than the raw-reading retention; narrower                     intervals only cover the retention window. Raw values, not calibrated.""")
    public SensorHistorySeriesResponse historyAggregate(
            @PathVariable Long id,
            @Parameter(description = "Inclusive range start, ISO-8601 instant")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @Parameter(description = "Inclusive range end, ISO-8601 instant; defaults to now")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @Parameter(
                            description =
                                    "Point width: 1m, 5m, 15m, 30m, 1h, 3h, 6h, 12h, 1d or 7d. Omit to get about 300 points.",
                            example = "1h")
                    @RequestParam(required = false)
                    String interval) {
        return sensorHistoryService.series(id, from, to, interval);
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
