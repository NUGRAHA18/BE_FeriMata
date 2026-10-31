package com.fertimata.backend.ai;

import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.mqtt.payload.AiDetectionPayload;
import com.fertimata.backend.plant.Plant;
import com.fertimata.backend.plant.PlantRepository;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores and serves edge vision results; runs no model, applies no confidence threshold and knows no disease
 * vocabulary, since interpreting detections is the AI team's contract. */
@Service
public class AiDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AiDetectionService.class);

    private final AiDetectionRepository detectionRepository;
    private final PlantRepository plantRepository;
    private final DeviceService deviceService;
    private final AiDetectionMapper mapper;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final JsonSupport jsonSupport;

    public AiDetectionService(
            AiDetectionRepository detectionRepository,
            PlantRepository plantRepository,
            DeviceService deviceService,
            AiDetectionMapper mapper,
            RealtimeEventPublisher realtimeEventPublisher,
            JsonSupport jsonSupport) {
        this.detectionRepository = detectionRepository;
        this.plantRepository = plantRepository;
        this.deviceService = deviceService;
        this.mapper = mapper;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.jsonSupport = jsonSupport;
    }

    /** Records a device detection; {@code deviceCode} comes from the topic. Empty when the device is unknown and
     * auto-registration is off. */
    @Transactional
    public Optional<AiDetectionResponse> record(String deviceCode, AiDetectionPayload payload) {
        boolean hasLabel = payload != null && payload.label() != null && !payload.label().isBlank();
        boolean hasScores = payload != null && payload.scores() != null && !payload.scores().isEmpty();
        if (!hasLabel && !hasScores) {
            log.warn("Ignoring AI detection from {} without a label or scores", deviceCode);
            return Optional.empty();
        }

        Optional<Device> device = deviceService.resolveForIngest(deviceCode);
        if (device.isEmpty()) {
            log.warn("Ignoring AI detection from unknown device {}", deviceCode);
            return Optional.empty();
        }

        AiDetection detection = new AiDetection();
        detection.setDevice(device.get());
        detection.setPlant(resolvePlant(payload.plantCode()));
        detection.setDetectionType(payload.detectionType() == null ? "UNSPECIFIED" : payload.detectionType());
        detection.setLabel(hasLabel ? truncate(payload.label().trim(), 128) : null);
        detection.setScores(hasScores ? jsonSupport.toJson(payload.scores()) : null);
        detection.setStationCode(blankToNull(payload.stationCode(), 32));
        String panDirection = blankToNull(payload.panDirection(), 16);
        detection.setPanDirection(panDirection == null ? null : panDirection.toUpperCase(Locale.ROOT));
        detection.setCaptureId(blankToNull(payload.captureId(), 64));
        detection.setConfidence(payload.confidence());
        detection.setImageUrl(payload.imageUrl());
        detection.setMetadata(jsonSupport.toJson(payload.metadata()));
        detection.setDetectedAt(payload.detectedAt() == null ? Instant.now() : payload.detectedAt());

        AiDetection saved = detectionRepository.save(detection);
        deviceService.recordContact(device.get(), saved.getDetectedAt(), null);

        AiDetectionResponse response = mapper.toResponse(saved);
        log.info(
                "AI detection recorded from {}: type={} station={} label={} scores={}",
                deviceCode,
                saved.getDetectionType(),
                saved.getStationCode(),
                saved.getLabel(),
                saved.getScores());
        realtimeEventPublisher.publish(RealtimeEventType.AI_DETECTION_CREATED, response);
        return Optional.of(response);
    }

    @Transactional(readOnly = true)
    public PageResponse<AiDetectionResponse> list(
            Long deviceId, String detectionType, String stationCode, Pageable pageable) {
        if (stationCode != null && !stationCode.isBlank()) {
            return PageResponse.from(
                    detectionRepository.findByStationCodeOrderByDetectedAtDesc(stationCode.trim(), pageable),
                    mapper::toResponse);
        }
        if (deviceId != null) {
            return PageResponse.from(
                    detectionRepository.findByDeviceIdOrderByDetectedAtDesc(deviceId, pageable), mapper::toResponse);
        }
        if (detectionType != null && !detectionType.isBlank()) {
            return PageResponse.from(
                    detectionRepository.findByDetectionTypeOrderByDetectedAtDesc(detectionType, pageable),
                    mapper::toResponse);
        }
        return PageResponse.from(detectionRepository.findRecent(pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AiDetectionResponse get(Long id) {
        return detectionRepository
                .findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("AiDetection", id));
    }

    @Transactional(readOnly = true)
    public Optional<AiDetectionResponse> latest() {
        return detectionRepository.findFirstByOrderByDetectedAtDescIdDesc().map(mapper::toResponse);
    }

    private static String blankToNull(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return truncate(value.trim(), max);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Resolves an optional plant; an unknown code neither errors nor creates a plant (per-plant tracking is undecided). */
    private Plant resolvePlant(String plantCode) {
        if (plantCode == null || plantCode.isBlank()) {
            return null;
        }
        Optional<Plant> plant = plantRepository.findByCode(plantCode);
        if (plant.isEmpty()) {
            log.debug("AI detection referenced unknown plant code {}; storing it unattached", plantCode);
        }
        return plant.orElse(null);
    }
}
