package com.fertimata.backend.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Read model for an AI detection. */
@Schema(name = "AiDetection")
public record AiDetectionResponse(
        Long id,
        Long deviceId,
        @Schema(example = "JETSON-001") String deviceCode,
        Long plantId,
        String plantCode,
        @Schema(example = "DISEASE") String detectionType,
        @Schema(description = "Class reported by the model; null for a scores-only multi-label result")
                String label,
        @Schema(example = "0.93") BigDecimal confidence,
        @Schema(description = "Label to score map from a multi-label model, stored as reported")
                Map<String, Object> scores,
        @Schema(example = "ST-01") String stationCode,
        String captureId,
        String imageUrl,
        Map<String, Object> metadata,
        Instant detectedAt,
        Instant createdAt) {}
