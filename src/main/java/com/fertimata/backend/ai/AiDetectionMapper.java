package com.fertimata.backend.ai;

import com.fertimata.backend.common.util.JsonSupport;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO conversion for detections.
 *
 * <p>A separate component because the metadata column has to be parsed from JSON, which needs the
 * shared {@code ObjectMapper} - a static {@code from} method on the record could not reach it.
 */
@Component
public class AiDetectionMapper {

    private final JsonSupport jsonSupport;

    public AiDetectionMapper(JsonSupport jsonSupport) {
        this.jsonSupport = jsonSupport;
    }

    public AiDetectionResponse toResponse(AiDetection detection) {
        return new AiDetectionResponse(
                detection.getId(),
                detection.getDevice().getId(),
                detection.getDevice().getDeviceCode(),
                detection.getPlant() == null ? null : detection.getPlant().getId(),
                detection.getPlant() == null ? null : detection.getPlant().getCode(),
                detection.getDetectionType(),
                detection.getLabel(),
                detection.getConfidence(),
                detection.getScores() == null ? null : jsonSupport.toMap(detection.getScores()),
                detection.getStationCode(),
                detection.getPanDirection(),
                detection.getCaptureId(),
                detection.getImageUrl(),
                jsonSupport.toMap(detection.getMetadata()),
                detection.getDetectedAt(),
                detection.getCreatedAt());
    }
}
