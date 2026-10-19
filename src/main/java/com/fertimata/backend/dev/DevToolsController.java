package com.fertimata.backend.dev;

import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.mqtt.MqttInboundDispatcher;
import com.fertimata.backend.mqtt.MqttTopicResolver;
import com.fertimata.backend.mqtt.TopicKind;
import com.fertimata.backend.mqtt.mock.LoopbackMqttPublisher;
import com.fertimata.backend.telemetry.TelemetryIngestService;
import com.fertimata.backend.telemetry.domain.TelemetrySource;
import com.fertimata.backend.telemetry.dto.TelemetryIngestRequest;
import com.fertimata.backend.telemetry.dto.TelemetryIngestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEVELOPMENT ONLY endpoints for driving the backend without hardware.
 *
 * <p>The whole controller is annotated {@code @Profile("dev")}, so these routes do not exist at all
 * in any other profile - they cannot be left switched on by a forgotten property. They still require
 * authentication, like every other {@code /api} route.
 *
 * <p>Injected messages take the same path a real device message would, so what a frontend developer
 * sees here is what they will see from real hardware.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@Tag(name = "Development tools", description = "DEVELOPMENT ONLY. Inject messages and inspect the mock broker.")
@SecurityRequirement(name = "bearerAuth")
public class DevToolsController {

    private static final Logger log = LoggerFactory.getLogger(DevToolsController.class);

    private final TelemetryIngestService ingestService;
    private final MqttInboundDispatcher dispatcher;
    private final MqttTopicResolver topicResolver;
    private final JsonSupport jsonSupport;
    private final ObjectProvider<LoopbackMqttPublisher> loopbackPublisher;

    public DevToolsController(
            TelemetryIngestService ingestService,
            MqttInboundDispatcher dispatcher,
            MqttTopicResolver topicResolver,
            JsonSupport jsonSupport,
            ObjectProvider<LoopbackMqttPublisher> loopbackPublisher) {
        this.ingestService = ingestService;
        this.dispatcher = dispatcher;
        this.topicResolver = topicResolver;
        this.jsonSupport = jsonSupport;
        this.loopbackPublisher = loopbackPublisher;
    }

    @PostMapping("/telemetry")
    @Operation(
            summary = "Inject a telemetry message",
            description = "Runs the same ingestion, validation and WebSocket push as a broker message.")
    public TelemetryIngestResult injectTelemetry(@Valid @RequestBody TelemetryIngestRequest request) {
        log.info("[DEV] Injecting telemetry for {}", request.deviceCode());
        return ingestService.ingest(
                request.deviceCode(), request.timestamp(), request.data(), TelemetrySource.DEV_API);
    }

    @PostMapping("/devices/{deviceCode}/heartbeat")
    @Operation(summary = "Send a heartbeat as if it came from the device")
    public ResponseEntity<Void> injectHeartbeat(@PathVariable String deviceCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", deviceCode);
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", "ONLINE");
        dispatch(TopicKind.STATUS, deviceCode, payload);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/devices/{deviceCode}/status")
    @Operation(
            summary = "Send an arbitrary status message as if it came from the device",
            description = "Use the actuators map to simulate reported actuator states, and powerSource "
                    + "(MAINS / BATTERY) to simulate an outage.")
    public ResponseEntity<Void> injectStatus(
            @PathVariable String deviceCode, @RequestBody Map<String, Object> payload) {
        dispatch(TopicKind.STATUS, deviceCode, withDefaults(deviceCode, payload));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/devices/{deviceCode}/ai-detections")
    @Operation(summary = "Send an AI detection as if it came from the device")
    public ResponseEntity<Void> injectDetection(
            @PathVariable String deviceCode, @RequestBody Map<String, Object> payload) {
        dispatch(TopicKind.AI, deviceCode, withDefaults(deviceCode, payload));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/devices/{deviceCode}/alerts")
    @Operation(
            summary = "Raise an alert as if the edge agent had detected a fault",
            description = "For example {\"type\": \"SSR_SHORT\", \"severity\": \"CRITICAL\", \"actuatorCode\": \"DIST-PUMP\"}.")
    public ResponseEntity<Void> injectAlert(
            @PathVariable String deviceCode, @RequestBody Map<String, Object> payload) {
        dispatch(TopicKind.ALERT, deviceCode, withDefaults(deviceCode, payload));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/devices/{deviceCode}/command-ack")
    @Operation(
            summary = "Acknowledge a command as if the device had executed it",
            description = "Lets the full command lifecycle, up to EXECUTED, be exercised without hardware.")
    public ResponseEntity<Void> injectCommandAck(
            @PathVariable String deviceCode, @RequestBody Map<String, Object> payload) {
        dispatch(TopicKind.COMMAND_ACK, deviceCode, withDefaults(deviceCode, payload));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/outbox")
    @Operation(
            summary = "Show what the backend would have published",
            description = "Only available while the mock transport is active (app.mqtt.enabled=false).")
    public List<LoopbackMqttPublisher.PublishedMessage> outbox() {
        LoopbackMqttPublisher publisher = loopbackPublisher.getIfAvailable();
        return publisher == null ? List.of() : publisher.recent();
    }

    private Map<String, Object> withDefaults(String deviceCode, Map<String, Object> payload) {
        Map<String, Object> enriched = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        enriched.putIfAbsent("deviceId", deviceCode);
        enriched.putIfAbsent("timestamp", Instant.now().toString());
        return enriched;
    }

    private void dispatch(TopicKind kind, String deviceCode, Map<String, Object> payload) {
        dispatcher.dispatch(topicResolver.topicFor(kind, deviceCode), jsonSupport.toJson(payload));
    }
}
