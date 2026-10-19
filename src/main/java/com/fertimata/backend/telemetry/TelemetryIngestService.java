package com.fertimata.backend.telemetry;

import com.fertimata.backend.automation.AutomationEngine;
import com.fertimata.backend.automation.AutomationSignal;
import com.fertimata.backend.common.exception.TelemetryValidationException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.config.TelemetryProperties;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.telemetry.domain.TelemetryMessage;
import com.fertimata.backend.telemetry.domain.TelemetryMessageRepository;
import com.fertimata.backend.telemetry.domain.TelemetrySource;
import com.fertimata.backend.telemetry.domain.TelemetryStatus;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import com.fertimata.backend.telemetry.dto.TelemetryIngestResult;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an inbound telemetry payload into stored readings and a realtime update.
 *
 * <p>This is the business half of the ingestion pipeline. The MQTT subscriber only deserialises and
 * calls in here; everything below - device resolution, metric mapping, validation, persistence and
 * the push to the PWA - lives in this class and is exercised identically whether a message arrived
 * from the broker, from the simulator, or from the development injection endpoint.
 *
 * <p>Two rules shape the error handling. A message the backend cannot fully understand is still
 * <em>recorded</em>, because the payload someone needs to debug is exactly the one that failed. And
 * one bad metric does not discard the good metrics in the same message.
 */
@Service
public class TelemetryIngestService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestService.class);

    private final TelemetryMessageRepository telemetryMessageRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceService deviceService;
    private final SensorService sensorService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AutomationEngine automationEngine;
    private final TelemetryProperties telemetryProperties;
    private final JsonSupport jsonSupport;

    public TelemetryIngestService(
            TelemetryMessageRepository telemetryMessageRepository,
            SensorReadingRepository sensorReadingRepository,
            DeviceService deviceService,
            SensorService sensorService,
            RealtimeEventPublisher realtimeEventPublisher,
            AutomationEngine automationEngine,
            TelemetryProperties telemetryProperties,
            JsonSupport jsonSupport) {
        this.telemetryMessageRepository = telemetryMessageRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.deviceService = deviceService;
        this.sensorService = sensorService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.automationEngine = automationEngine;
        this.telemetryProperties = telemetryProperties;
        this.jsonSupport = jsonSupport;
    }

    /**
     * Ingests one telemetry message.
     *
     * @param deviceCode taken from the transport (the MQTT topic), never from the payload body: a
     *     device must not be able to write telemetry attributed to another device
     * @throws TelemetryValidationException when the message carries nothing usable at all
     */
    @Transactional
    public TelemetryIngestResult ingest(
            String deviceCode, Instant deviceTimestamp, Map<String, Object> data, TelemetrySource source) {

        Instant receivedAt = Instant.now();
        validateEnvelope(deviceCode, data);

        Instant recordedAt = normaliseTimestamp(deviceCode, deviceTimestamp, receivedAt);
        Optional<Device> device = deviceService.resolveForIngest(deviceCode);

        TelemetryMessage message = new TelemetryMessage();
        message.setDeviceCode(deviceCode);
        message.setDevice(device.orElse(null));
        message.setPayload(jsonSupport.toJson(rawEnvelope(deviceCode, recordedAt, data)));
        message.setSource(source);
        message.setRecordedAt(recordedAt);
        message.setReceivedAt(receivedAt);

        if (device.isEmpty()) {
            message.setStatus(TelemetryStatus.REJECTED);
            message.setMetricCount(0);
            message.setErrorMessage("Unknown device code " + deviceCode);
            TelemetryMessage saved = telemetryMessageRepository.save(message);
            log.warn("Rejected telemetry from unknown device {}", deviceCode);
            return new TelemetryIngestResult(
                    saved.getId(),
                    deviceCode,
                    TelemetryStatus.REJECTED,
                    0,
                    List.copyOf(data.keySet()),
                    saved.getErrorMessage());
        }

        Device resolvedDevice = device.get();
        List<String> rejectedMetrics = new ArrayList<>();
        List<SensorReading> readings = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String metricKey = entry.getKey();
            Optional<MetricValue> value = MetricValue.parse(entry.getValue());
            if (value.isEmpty()) {
                rejectedMetrics.add(metricKey);
                continue;
            }
            Optional<Sensor> sensor = sensorService.resolveForMetric(resolvedDevice, metricKey);
            if (sensor.isEmpty()) {
                rejectedMetrics.add(metricKey);
                continue;
            }
            readings.add(buildReading(message, resolvedDevice, sensor.get(), metricKey, value.get(), recordedAt,
                    receivedAt));
        }

        message.setMetricCount(readings.size());
        message.setStatus(resolveStatus(readings.size(), rejectedMetrics));
        if (!rejectedMetrics.isEmpty()) {
            message.setErrorMessage("Unmapped or unusable metrics: " + String.join(", ", rejectedMetrics));
        }

        TelemetryMessage savedMessage = telemetryMessageRepository.save(message);
        List<SensorReading> savedReadings = sensorReadingRepository.saveAll(readings);

        deviceService.recordContact(resolvedDevice, recordedAt, null);
        publishUpdate(resolvedDevice, recordedAt, savedReadings);
        savedReadings.forEach(this::notifyAutomation);

        log.info(
                "Telemetry ingested from {} via {}: {} reading(s) stored, {} rejected",
                deviceCode,
                source,
                savedReadings.size(),
                rejectedMetrics.size());

        return new TelemetryIngestResult(
                savedMessage.getId(),
                deviceCode,
                savedMessage.getStatus(),
                savedReadings.size(),
                List.copyOf(rejectedMetrics),
                savedMessage.getErrorMessage());
    }

    private void validateEnvelope(String deviceCode, Map<String, Object> data) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new TelemetryValidationException("Telemetry message has no device code");
        }
        if (data == null || data.isEmpty()) {
            throw new TelemetryValidationException(
                    "Telemetry message from %s carries no data".formatted(deviceCode));
        }
        if (data.size() > telemetryProperties.maxMetricsPerMessage()) {
            throw new TelemetryValidationException(
                    "Telemetry message from %s carries %d metrics, more than the configured maximum of %d"
                            .formatted(deviceCode, data.size(), telemetryProperties.maxMetricsPerMessage()),
                    Map.of("metricCount", data.size()));
        }
    }

    /**
     * Keeps a device with a wrong clock from writing history in the future.
     *
     * <p>A missing timestamp is not an error - it simply means the device did not tell us, so the
     * arrival time is used. Only a timestamp that is implausibly ahead is overridden; a timestamp in
     * the past is kept, because that is what a device replaying buffered readings looks like.
     */
    private Instant normaliseTimestamp(String deviceCode, Instant deviceTimestamp, Instant receivedAt) {
        if (deviceTimestamp == null) {
            return receivedAt;
        }
        if (deviceTimestamp.isAfter(receivedAt.plus(telemetryProperties.maxClockSkew()))) {
            log.warn(
                    "Device {} reported timestamp {} beyond the allowed clock skew; using the receive time instead",
                    deviceCode,
                    deviceTimestamp);
            return receivedAt;
        }
        return deviceTimestamp;
    }

    private Map<String, Object> rawEnvelope(String deviceCode, Instant recordedAt, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("deviceId", deviceCode);
        envelope.put("timestamp", recordedAt.toString());
        envelope.put("data", data);
        return envelope;
    }

    private SensorReading buildReading(
            TelemetryMessage message,
            Device device,
            Sensor sensor,
            String metricKey,
            MetricValue value,
            Instant recordedAt,
            Instant receivedAt) {
        SensorReading reading = new SensorReading();
        reading.setTelemetryMessage(message);
        reading.setDevice(device);
        reading.setSensor(sensor);
        reading.setMetricKey(metricKey);
        reading.setNumericValue(value.numeric());
        reading.setTextValue(value.text());
        // The unit is the sensor's, not the payload's: a device sending a bare number must not be
        // able to silently change what that number means.
        reading.setUnit(sensor.getUnit());
        reading.setRecordedAt(recordedAt);
        reading.setReceivedAt(receivedAt);
        return reading;
    }

    private TelemetryStatus resolveStatus(int accepted, List<String> rejected) {
        if (accepted == 0) {
            return TelemetryStatus.REJECTED;
        }
        return rejected.isEmpty() ? TelemetryStatus.ACCEPTED : TelemetryStatus.PARTIALLY_ACCEPTED;
    }

    private void publishUpdate(Device device, Instant recordedAt, List<SensorReading> readings) {
        if (readings.isEmpty()) {
            return;
        }
        List<SensorReadingResponse> payload =
                readings.stream().map(SensorReadingResponse::from).toList();
        realtimeEventPublisher.publish(
                RealtimeEventType.SENSOR_READING_UPDATED,
                new TelemetryUpdate(device.getId(), device.getDeviceCode(), recordedAt, payload));
    }

    private void notifyAutomation(SensorReading reading) {
        automationEngine.evaluate(new AutomationSignal(
                reading.getDevice().getId(),
                reading.getDevice().getDeviceCode(),
                reading.getSensor().getId(),
                reading.getMetricKey(),
                reading.getNumericValue(),
                reading.getRecordedAt()));
    }

    /**
     * Payload of a {@code SENSOR_READING_UPDATED} event.
     *
     * <p>One event per message rather than one per metric: a message carrying six metrics is one
     * dashboard update, and sending six frames would make the client reassemble what the device
     * already sent together.
     */
    public record TelemetryUpdate(
            Long deviceId, String deviceCode, Instant recordedAt, List<SensorReadingResponse> readings) {}

    /**
     * A telemetry value after type inspection.
     *
     * <p>Numbers are stored numerically so history queries can aggregate them; anything else that is
     * still meaningful is kept as text rather than dropped. Structured values (objects, arrays) are
     * rejected: nesting would need a contract that does not exist yet.
     */
    private record MetricValue(BigDecimal numeric, String text) {

        static Optional<MetricValue> parse(Object raw) {
            if (raw instanceof BigDecimal value) {
                return Optional.of(new MetricValue(value, null));
            }
            if (raw instanceof Number value) {
                return Optional.of(new MetricValue(new BigDecimal(value.toString()), null));
            }
            if (raw instanceof Boolean value) {
                return Optional.of(new MetricValue(null, value.toString()));
            }
            if (raw instanceof String value) {
                return parseString(value);
            }
            // null, objects and arrays: nothing meaningful can be stored without a contract.
            return Optional.empty();
        }

        private static Optional<MetricValue> parseString(String value) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new MetricValue(new BigDecimal(trimmed), null));
            } catch (NumberFormatException ex) {
                return Optional.of(new MetricValue(null, trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed));
            }
        }
    }
}
