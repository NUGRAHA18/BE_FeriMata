package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Vision result. TODO(AI): contract not agreed; extra fields are kept in {@code metadata}. Rev A: {@code scores}
 * per label (N/P/K stress), {@code captureId} groups crops of one frame, {@code panDirection} TIMUR/BARAT. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiDetectionPayload(
        String deviceId,
        Instant detectedAt,
        String detectionType,
        String label,
        BigDecimal confidence,
        String imageUrl,
        String plantCode,
        Map<String, BigDecimal> scores,
        String stationCode,
        String panDirection,
        String captureId,
        Map<String, Object> metadata) {}
