package com.fertimata.backend.telemetry.dto;

import com.fertimata.backend.telemetry.domain.TelemetryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Outcome of ingesting one telemetry message.
 *
 * <p>Returned by the development injection endpoint and used in logs. Rejected metrics are named so
 * that a misconfigured device is obvious instead of silently producing no data.
 */
@Schema(name = "TelemetryIngestResult")
public record TelemetryIngestResult(
        Long telemetryMessageId,
        String deviceCode,
        TelemetryStatus status,
        int acceptedMetrics,
        List<String> rejectedMetrics,
        String errorMessage) {}
