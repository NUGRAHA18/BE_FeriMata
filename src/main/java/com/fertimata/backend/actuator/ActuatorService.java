package com.fertimata.backend.actuator;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.actuator.dto.ActuatorRegistrationRequest;
import com.fertimata.backend.actuator.dto.ActuatorResponse;
import com.fertimata.backend.actuator.dto.ActuatorStatusResponse;
import com.fertimata.backend.actuator.dto.ActuatorUpdateRequest;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Actuator registry and reported-state bookkeeping. */
@Service
public class ActuatorService {

    private static final Logger log = LoggerFactory.getLogger(ActuatorService.class);

    private final ActuatorRepository actuatorRepository;
    private final DeviceService deviceService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final JsonSupport jsonSupport;

    public ActuatorService(
            ActuatorRepository actuatorRepository,
            DeviceService deviceService,
            RealtimeEventPublisher realtimeEventPublisher,
            JsonSupport jsonSupport) {
        this.actuatorRepository = actuatorRepository;
        this.deviceService = deviceService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.jsonSupport = jsonSupport;
    }

    @Transactional(readOnly = true)
    public List<ActuatorResponse> list() {
        return actuatorRepository.findAllWithDevice().stream()
                .map(ActuatorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActuatorResponse> listByDevice(Long deviceId) {
        deviceService.require(deviceId);
        return actuatorRepository.findByDeviceIdOrderByCodeAsc(deviceId).stream()
                .map(ActuatorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ActuatorResponse get(Long id) {
        return ActuatorResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public ActuatorStatusResponse status(Long id) {
        return ActuatorStatusResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Actuator require(Long id) {
        return actuatorRepository
                .findByIdWithDevice(id)
                .orElseThrow(() -> new ResourceNotFoundException("Actuator", id));
    }

    @Transactional
    public ActuatorResponse register(ActuatorRegistrationRequest request) {
        Device device = deviceService.require(request.deviceId());
        actuatorRepository.findByDeviceIdAndCode(device.getId(), request.code()).ifPresent(existing -> {
            throw new BusinessRuleException(
                    "Actuator code %s already exists on device %s".formatted(request.code(), device.getDeviceCode()),
                    Map.of("code", request.code()));
        });

        Actuator actuator = new Actuator(device, request.code(), request.name(), request.type());
        actuator.setDescription(request.description());
        actuator.setMaxRunSeconds(request.maxRunSeconds());
        actuator.setMetadata(jsonSupport.toJson(request.metadata()));
        Actuator saved = actuatorRepository.save(actuator);
        log.info("Actuator registered: {} on device {}", saved.getCode(), device.getDeviceCode());
        return ActuatorResponse.from(saved);
    }

    @Transactional
    public ActuatorResponse update(Long id, ActuatorUpdateRequest request) {
        Actuator actuator = require(id);
        if (request.name() != null) {
            actuator.setName(request.name());
        }
        if (request.type() != null) {
            actuator.setType(request.type());
        }
        if (request.description() != null) {
            actuator.setDescription(request.description());
        }
        if (request.enabled() != null) {
            actuator.setEnabled(request.enabled());
        }
        if (request.maxRunSeconds() != null) {
            actuator.setMaxRunSeconds(request.maxRunSeconds() == 0 ? null : request.maxRunSeconds());
        }
        if (request.metadata() != null) {
            actuator.setMetadata(jsonSupport.toJson(request.metadata()));
        }
        log.info(
                "Actuator {} on {} updated (enabled={}, maxRunSeconds={})",
                actuator.getCode(),
                actuator.getDevice().getDeviceCode(),
                actuator.isEnabled(),
                actuator.getMaxRunSeconds());
        return ActuatorResponse.from(actuator);
    }

    /**
     * Records a state the device reported for one of its actuators.
     *
     * <p>Unknown actuator codes are logged and ignored rather than auto-created: an actuator is
     * something an operator can switch on, and inventing one from an unverified message would put a
     * control in the UI that nobody provisioned.
     */
    @Transactional
    public void reportState(Device device, String actuatorCode, String state, Instant reportedAt) {
        Optional<Actuator> actuator = actuatorRepository.findByDeviceIdAndCode(device.getId(), actuatorCode);
        if (actuator.isEmpty()) {
            log.warn("Device {} reported state for unknown actuator {}", device.getDeviceCode(), actuatorCode);
            return;
        }
        Actuator target = actuator.get();
        boolean changed = !java.util.Objects.equals(state, target.getCurrentState());
        target.reportState(state, reportedAt);

        // A repeated report is proof of life, not news: refresh the timestamp but do not wake up
        // every connected dashboard for a state that did not change.
        if (changed) {
            log.info("Actuator {} on {} reported state {}", actuatorCode, device.getDeviceCode(), state);
            realtimeEventPublisher.publish(
                    RealtimeEventType.ACTUATOR_STATUS_CHANGED, ActuatorStatusResponse.from(target));
        }
    }

    @Transactional(readOnly = true)
    public List<ActuatorStatusResponse> allStatuses() {
        return actuatorRepository.findAllWithDevice().stream()
                .map(ActuatorStatusResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return actuatorRepository.count();
    }
}
