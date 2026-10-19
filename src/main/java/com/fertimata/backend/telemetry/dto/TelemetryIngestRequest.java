package com.fertimata.backend.telemetry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/**
 * Telemetry submitted through the development-only injection endpoint.
 *
 * <p>Mirrors the MQTT payload so that testing through REST exercises the same ingestion path the
 * broker uses.
 */
@Schema(name = "TelemetryIngestRequest")
public record TelemetryIngestRequest(
        @NotBlank @Size(max = 64) @Schema(example = "JETSON-001") String deviceCode,
        @Schema(description = "Device-side timestamp; defaults to now when omitted") Instant timestamp,
        @NotEmpty @Schema(example = "{\"temperature\": 28.4, \"ph\": 6.2}") Map<String, Object> data) {}
