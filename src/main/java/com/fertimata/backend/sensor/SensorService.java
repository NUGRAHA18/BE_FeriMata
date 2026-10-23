package com.fertimata.backend.sensor;

import com.fertimata.backend.calibration.ReadingCalibrator;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.config.TelemetryProperties;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sensor registry and measurement queries.
 *
 * <p>A sensor is a row, never a class or a table. Adding pH, EC, NPK or anything else the hardware
 * team decides on later is a data change, which is the whole point of this design.
 */
@Service
public class SensorService {

    private static final Logger log = LoggerFactory.getLogger(SensorService.class);

    private final SensorRepository sensorRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceService deviceService;
    private final TelemetryProperties telemetryProperties;
    private final JsonSupport jsonSupport;
    private final ReadingCalibrator readingCalibrator;

    public SensorService(
            SensorRepository sensorRepository,
            SensorReadingRepository sensorReadingRepository,
            DeviceService deviceService,
            TelemetryProperties telemetryProperties,
            JsonSupport jsonSupport,
            ReadingCalibrator readingCalibrator) {
        this.sensorRepository = sensorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.deviceService = deviceService;
        this.telemetryProperties = telemetryProperties;
        this.jsonSupport = jsonSupport;
        this.readingCalibrator = readingCalibrator;
    }

    @Transactional(readOnly = true)
    public List<SensorResponse> list() {
        return sensorRepository.findAllWithDevice().stream().map(SensorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SensorResponse> listByDevice(Long deviceId) {
        deviceService.require(deviceId);
        return sensorRepository.findByDeviceIdOrderByCodeAsc(deviceId).stream()
                .map(SensorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SensorResponse get(Long id) {
        return SensorResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Sensor require(Long id) {
        return sensorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sensor", id));
    }

    @Transactional
    public SensorResponse register(SensorRegistrationRequest request) {
        Device device = deviceService.require(request.deviceId());
        sensorRepository.findByDeviceIdAndCode(device.getId(), request.code()).ifPresent(existing -> {
            throw new BusinessRuleException(
                    "Sensor code %s already exists on device %s".formatted(request.code(), device.getDeviceCode()),
                    Map.of("code", request.code()));
        });
        sensorRepository
                .findByDeviceIdAndMetricKey(device.getId(), request.metricKey())
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "Metric key %s is already mapped to sensor %s on device %s"
                                    .formatted(request.metricKey(), existing.getCode(), device.getDeviceCode()),
                            Map.of("metricKey", request.metricKey()));
                });

        Sensor sensor = new Sensor(
                device, request.code(), request.metricKey(), request.name(), request.type(), request.unit());
        sensor.setDescription(request.description());
        sensor.setMetadata(jsonSupport.toJson(request.metadata()));
        Sensor saved = sensorRepository.save(sensor);
        log.info("Sensor registered: {} ({}) on device {}", saved.getCode(), saved.getMetricKey(),
                device.getDeviceCode());
        return SensorResponse.from(saved);
    }

    @Transactional
    public SensorResponse update(Long id, SensorUpdateRequest request) {
        Sensor sensor = require(id);
        if (request.name() != null) {
            sensor.setName(request.name());
        }
        if (request.type() != null) {
            sensor.setType(request.type());
        }
        if (request.unit() != null) {
            sensor.setUnit(request.unit());
        }
        if (request.description() != null) {
            sensor.setDescription(request.description());
        }
        if (request.metadata() != null) {
            sensor.setMetadata(jsonSupport.toJson(request.metadata()));
        }
        if (request.enabled() != null) {
            sensor.setEnabled(request.enabled());
            // Once an operator has reviewed it, it is no longer an unreviewed automatic row.
            if (request.enabled()) {
                sensor.setAutoRegistered(false);
            }
        }
        return SensorResponse.from(sensor);
    }

    /**
     * Finds the sensor a telemetry metric belongs to.
     *
     * <p>When the metric is unknown the configured policy decides: either create a disabled
     * placeholder so the value can still be stored and reviewed later, or report it as unmapped so
     * the caller can record a partial ingest. Neither path invents a unit or a sensor type.
     */
    @Transactional
    public Optional<Sensor> resolveForMetric(Device device, String metricKey) {
        Optional<Sensor> existing = sensorRepository.findByDeviceIdAndMetricKey(device.getId(), metricKey);
        if (existing.isPresent()) {
            return existing;
        }
        if (!telemetryProperties.autoRegisterSensors()) {
            return Optional.empty();
        }
        if (sensorRepository.findByDeviceIdAndCode(device.getId(), metricKey).isPresent()) {
            // A sensor already owns this code under a different metric key; do not shadow it.
            log.warn(
                    "Cannot auto-register metric {} on device {}: the code is already taken",
                    metricKey,
                    device.getDeviceCode());
            return Optional.empty();
        }

        Sensor sensor = new Sensor(device, metricKey, metricKey, metricKey, null, null);
        sensor.setEnabled(false);
        sensor.setAutoRegistered(true);
        sensor.setDescription("Auto-registered from telemetry; review name, type and unit before enabling");
        Sensor saved = sensorRepository.save(sensor);
        log.info("Auto-registered sensor for unknown metric {} on device {}", metricKey, device.getDeviceCode());
        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public SensorReadingResponse latest(Long sensorId) {
        require(sensorId);
        return sensorReadingRepository
                .findFirstBySensorIdOrderByRecordedAtDescIdDesc(sensorId)
                .map(readingCalibrator::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Latest reading for sensor", sensorId));
    }

    @Transactional(readOnly = true)
    public PageResponse<SensorReadingResponse> history(Long sensorId, Instant from, Instant to, Pageable pageable) {
        require(sensorId);
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(java.time.Duration.ofDays(1)) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("The start of the range must not be after its end");
        }
        Page<SensorReading> page = sensorReadingRepository.findBySensorIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                sensorId, start, end, pageable);
        // Dikoreksi per halaman, bukan per baris: kovariat satu halaman diambil dengan satu query.
        List<SensorReadingResponse> content = readingCalibrator.toResponses(page.getContent());
        return PageResponse.of(page, content);
    }

    /** Latest value of every sensor, in one query, for the dashboard. */
    @Transactional(readOnly = true)
    public List<SensorReadingResponse> latestPerSensor() {
        return readingCalibrator.toResponses(sensorReadingRepository.findLatestPerSensor());
    }

    @Transactional(readOnly = true)
    public long countEnabled() {
        return sensorRepository.countByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return sensorRepository.count();
    }

    /** Exposed for the ingestion pipeline, which already holds a managed reading. */
    public SensorReadingResponse toResponse(SensorReading reading) {
        return readingCalibrator.toResponse(reading);
    }

    /** Satu pesan telemetri sekaligus, supaya kovariat kalibrasi diambil dari pesan yang sama. */
    public List<SensorReadingResponse> toResponses(List<SensorReading> readings) {
        return readingCalibrator.toResponses(readings);
    }
}
