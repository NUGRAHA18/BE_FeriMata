package com.fertimata.backend.telemetry.dto;

import com.fertimata.backend.telemetry.domain.TelemetryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Ingestion outcome; rejected metrics are named so a misconfigured device is obvious. */
@Schema(name = "TelemetryIngestResult")
public record TelemetryIngestResult(
        Long telemetryMessageId,
        String deviceCode,
        TelemetryStatus status,
        int acceptedMetrics,
        List<String> rejectedMetrics,
        String errorMessage) {}
