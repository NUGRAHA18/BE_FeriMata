package com.fertimata.backend.metrics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@Tag(name = "Metrics", description = "Technical system metrics for the report (Protokol-Data.md §6)")
@SecurityRequirement(name = "bearerAuth")
public class SystemMetricsController {

    private final SystemMetricsService service;

    public SystemMetricsController(SystemMetricsService service) {
        this.service = service;
    }

    @GetMapping("/system")
    @Operation(
            summary = "Uptime, latency, data completeness, actuation, energy and fertilizer volume",
            description = "Defaults to the last 7 days. Computed from stored readings, so a range older than the reading retention looks empty.")
    public SystemMetricsResponse system(
            @Parameter(description = "Range start, ISO-8601 instant")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @Parameter(description = "Range end, ISO-8601 instant")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to) {
        return service.metrics(from, to);
    }
}
