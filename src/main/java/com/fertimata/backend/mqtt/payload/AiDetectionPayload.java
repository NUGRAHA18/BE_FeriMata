package com.fertimata.backend.mqtt.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of a result from the vision model running on the device.
 *
 * <p>TODO(AI): the output contract is not agreed yet. Label vocabulary, whether bounding boxes are
 * included and how images are transported are all open; extra fields are accepted and preserved in
 * {@code metadata} rather than rejected.
 *
 * <p>FERTIMATA Rev A sends a multi-label result: {@code scores} holds one value per label (N, P and K
 * stress), {@code label} may then be omitted, {@code stationCode} names the trolley stop and
 * {@code captureId} ties together the results cropped from one frame. {@code panDirection} says
 * whether the camera faced east or west (TIMUR / BARAT), since every station is shot both ways.
 */
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
