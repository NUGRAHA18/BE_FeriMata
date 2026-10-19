package com.fertimata.backend.mqtt.handler;

import com.fertimata.backend.ai.AiDetectionService;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.mqtt.MqttMessageHandler;
import com.fertimata.backend.mqtt.TopicKind;
import com.fertimata.backend.mqtt.payload.AiDetectionPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stores a detection reported by the vision model running on the device. */
@Component
public class AiDetectionMqttHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(AiDetectionMqttHandler.class);

    private final AiDetectionService detectionService;
    private final JsonSupport jsonSupport;

    public AiDetectionMqttHandler(AiDetectionService detectionService, JsonSupport jsonSupport) {
        this.detectionService = detectionService;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public TopicKind handles() {
        return TopicKind.AI;
    }

    @Override
    public void handle(String deviceCode, String payload) {
        AiDetectionPayload detection;
        try {
            detection = jsonSupport.fromJson(payload, AiDetectionPayload.class);
        } catch (RuntimeException ex) {
            log.warn("Discarding malformed AI detection from {}: {}", deviceCode, ex.getMessage());
            return;
        }
        detectionService.record(deviceCode, detection);
    }
}
