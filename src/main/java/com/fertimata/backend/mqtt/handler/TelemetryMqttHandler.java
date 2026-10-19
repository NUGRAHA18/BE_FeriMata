package com.fertimata.backend.mqtt.handler;

import com.fertimata.backend.common.exception.TelemetryValidationException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.mqtt.MqttMessageHandler;
import com.fertimata.backend.mqtt.TopicKind;
import com.fertimata.backend.mqtt.payload.TelemetryPayload;
import com.fertimata.backend.telemetry.TelemetryIngestService;
import com.fertimata.backend.telemetry.domain.TelemetrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deserialises a telemetry message and hands it to the ingestion service.
 *
 * <p>That is the whole job. Device resolution, metric mapping, validation and persistence all belong
 * to {@link TelemetryIngestService}, so the same logic runs whether the message came from the
 * broker, the simulator or the development endpoint.
 */
@Component
public class TelemetryMqttHandler implements MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryMqttHandler.class);

    private final TelemetryIngestService ingestService;
    private final JsonSupport jsonSupport;

    public TelemetryMqttHandler(TelemetryIngestService ingestService, JsonSupport jsonSupport) {
        this.ingestService = ingestService;
        this.jsonSupport = jsonSupport;
    }

    @Override
    public TopicKind handles() {
        return TopicKind.TELEMETRY;
    }

    @Override
    public void handle(String deviceCode, String payload) {
        TelemetryPayload telemetry;
        try {
            telemetry = jsonSupport.fromJson(payload, TelemetryPayload.class);
        } catch (RuntimeException ex) {
            log.warn("Discarding malformed telemetry from {}: {}", deviceCode, ex.getMessage());
            return;
        }
        if (telemetry == null) {
            log.warn("Discarding empty telemetry message from {}", deviceCode);
            return;
        }

        try {
            ingestService.ingest(deviceCode, telemetry.timestamp(), telemetry.data(), TelemetrySource.MQTT);
        } catch (TelemetryValidationException ex) {
            // Expected for a device sending something unusable; not worth a stack trace.
            log.warn("Rejected telemetry from {}: {}", deviceCode, ex.getMessage());
        }
    }
}
