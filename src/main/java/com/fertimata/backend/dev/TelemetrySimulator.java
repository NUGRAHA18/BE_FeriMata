package com.fertimata.backend.dev;

import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.mqtt.MqttInboundDispatcher;
import com.fertimata.backend.mqtt.MqttTopicResolver;
import com.fertimata.backend.mqtt.TopicKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY. Produces sample telemetry so the dashboard is alive without hardware.
 *
 * <p>It feeds messages into {@link MqttInboundDispatcher} on a real topic rather than calling the
 * ingestion service directly. That way the simulated path exercises the same topic parsing, handler
 * routing, validation and WebSocket push that a real device will, and the frontend developer is
 * looking at the production pipeline rather than at a shortcut.
 *
 * <p>Every configured device gets a heartbeat, including devices without metrics, so that relay
 * modules show ONLINE and accept commands the way they will once the edge agent polls them.
 *
 * <p>Values are random inside configured ranges. They are placeholders for rendering, not plausible
 * agronomic readings, and nothing in the backend interprets them.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.dev.simulator", name = "enabled", havingValue = "true")
public class TelemetrySimulator {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulator.class);
    private static final int DEFAULT_SCALE = 2;

    private final MqttInboundDispatcher dispatcher;
    private final MqttTopicResolver topicResolver;
    private final JsonSupport jsonSupport;
    private final DevProperties properties;

    public TelemetrySimulator(
            MqttInboundDispatcher dispatcher,
            MqttTopicResolver topicResolver,
            JsonSupport jsonSupport,
            DevProperties properties) {
        this.dispatcher = dispatcher;
        this.topicResolver = topicResolver;
        this.jsonSupport = jsonSupport;
        this.properties = properties;
        log.warn("DEVELOPMENT telemetry simulator is active. It must never run outside the dev profile.");
    }

    @Scheduled(fixedDelayString = "${app.dev.simulator.interval}")
    public void emitTelemetry() {
        DevProperties.Simulator simulator = properties.simulator();
        if (simulator == null) {
            return;
        }
        for (DevProperties.SimulatedDevice device : simulator.devices()) {
            if (device.code() == null || device.metrics().isEmpty()) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            device.metrics().stream()
                    .filter(metric -> metric.key() != null)
                    .forEach(metric -> data.put(metric.key(), randomValue(metric)));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deviceId", device.code());
            payload.put("timestamp", Instant.now().toString());
            payload.put("data", data);
            dispatch(TopicKind.TELEMETRY, device.code(), jsonSupport.toJson(payload));
        }
    }

    /** Heartbeats are separate so device liveness can be seen working on its own. */
    @Scheduled(fixedDelayString = "${app.dev.simulator.interval}")
    public void emitHeartbeat() {
        DevProperties.Simulator simulator = properties.simulator();
        if (simulator == null) {
            return;
        }
        for (DevProperties.SimulatedDevice device : simulator.devices()) {
            if (device.code() == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deviceId", device.code());
            payload.put("timestamp", Instant.now().toString());
            payload.put("status", "ONLINE");
            if (device.powerSource() != null) {
                payload.put("powerSource", device.powerSource());
            }
            dispatch(TopicKind.STATUS, device.code(), jsonSupport.toJson(payload));
        }
    }

    private void dispatch(TopicKind kind, String deviceCode, String payload) {
        if (!topicResolver.hasTemplate(kind)) {
            return;
        }
        dispatcher.dispatch(topicResolver.topicFor(kind, deviceCode), payload);
    }

    private BigDecimal randomValue(DevProperties.Metric metric) {
        BigDecimal min = metric.min() == null ? BigDecimal.ZERO : metric.min();
        BigDecimal max = metric.max() == null ? BigDecimal.ONE : metric.max();
        if (max.compareTo(min) < 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }
        double factor = ThreadLocalRandom.current().nextDouble();
        BigDecimal value = min.add(max.subtract(min).multiply(BigDecimal.valueOf(factor)));
        int scale = metric.scale() == null ? DEFAULT_SCALE : metric.scale();
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
