package com.fertimata.backend.device;

import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.alert.AlertSource;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.config.DeviceProperties;
import com.fertimata.backend.config.PowerProperties;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Device registry and liveness: ONLINE when heard from, OFFLINE after the configured timeout. Observed, never assumed. */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final DeviceProperties deviceProperties;
    private final PowerProperties powerProperties;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AlertService alertService;
    private final JsonSupport jsonSupport;

    public DeviceService(
            DeviceRepository deviceRepository,
            DeviceProperties deviceProperties,
            PowerProperties powerProperties,
            RealtimeEventPublisher realtimeEventPublisher,
            AlertService alertService,
            JsonSupport jsonSupport) {
        this.deviceRepository = deviceRepository;
        this.deviceProperties = deviceProperties;
        this.powerProperties = powerProperties;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.alertService = alertService;
        this.jsonSupport = jsonSupport;
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        return deviceRepository.findAllByOrderByDeviceCodeAsc().stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(Long id) {
        return DeviceResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public DeviceStatusResponse status(Long id) {
        return toStatus(require(id));
    }

    public DeviceStatusResponse toStatus(Device device) {
        Long secondsSinceLastSeen = device.getLastSeenAt() == null
                ? null
                : Duration.between(device.getLastSeenAt(), Instant.now()).toSeconds();
        return new DeviceStatusResponse(
                device.getId(),
                device.getDeviceCode(),
                device.getStatus(),
                device.getLastSeenAt(),
                secondsSinceLastSeen,
                deviceProperties.offlineTimeout().toSeconds(),
                device.getPowerSource(),
                device.getPowerSourceUpdatedAt(),
                isOnBackupPower(device));
    }

    private boolean isOnBackupPower(Device device) {
        return device.getPowerSource() != null && !powerProperties.isNormal(device.getPowerSource());
    }

    @Transactional
    public DeviceResponse register(DeviceRegistrationRequest request) {
        if (deviceRepository.existsByDeviceCode(request.deviceCode())) {
            throw new BusinessRuleException(
                    "A device with code %s is already registered".formatted(request.deviceCode()),
                    Map.of("deviceCode", request.deviceCode()));
        }
        Device device = new Device(request.deviceCode(), request.name(), request.type());
        device.setDescription(request.description());
        Device saved = deviceRepository.save(device);
        log.info("Device registered: {}", saved.getDeviceCode());
        return DeviceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Device require(Long id) {
        return deviceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Device", id));
    }

    @Transactional(readOnly = true)
    public Optional<Device> findByCode(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode);
    }

    /** Resolves a message's device. Unknown code: placeholder when {@code app.device.auto-register}, else reported unknown. */
    @Transactional
    public Optional<Device> resolveForIngest(String deviceCode) {
        Optional<Device> existing = deviceRepository.findByDeviceCode(deviceCode);
        if (existing.isPresent()) {
            return existing;
        }
        if (!deviceProperties.autoRegister()) {
            log.warn("Message from unregistered device {} (auto-registration is disabled)", deviceCode);
            return Optional.empty();
        }
        Device device = new Device(deviceCode, deviceCode, null);
        device.setDescription("Auto-registered on first contact");
        Device saved = deviceRepository.save(device);
        log.info("Auto-registered device {} on first contact", deviceCode);
        return Optional.of(saved);
    }

    /** Records contact on every inbound message, not just heartbeats, so a busy device does not flap offline. */
    @Transactional
    public void recordContact(Device device, Instant seenAt, Map<String, Object> metadata) {
        DeviceStatus previous = device.getStatus();
        device.markSeen(seenAt == null ? Instant.now() : seenAt);
        if (metadata != null && !metadata.isEmpty()) {
            device.setMetadata(jsonSupport.toJson(metadata));
        }

        if (previous != DeviceStatus.ONLINE) {
            log.info("Device {} is now ONLINE (was {})", device.getDeviceCode(), previous);
            realtimeEventPublisher.publish(RealtimeEventType.DEVICE_STATUS_CHANGED, toStatus(device));
            if (previous == DeviceStatus.OFFLINE) {
                alertService.raise(NewAlert.builder(AlertType.DEVICE_RECOVERED, AlertSeverity.INFO)
                        .title("Device back online")
                        .message("Device %s started reporting again".formatted(device.getDeviceCode()))
                        .source(AlertSource.SYSTEM)
                        .device(device)
                        .build());
            }
        }
    }

    /** Records the reported power source; only a change raises an alert/event, repeats just refresh the timestamp. */
    @Transactional
    public void reportPowerSource(Device device, String reportedSource, Instant reportedAt) {
        String source = PowerProperties.normalise(reportedSource);
        if (source == null || source.isEmpty()) {
            return;
        }
        String previous = device.getPowerSource();
        device.reportPowerSource(source, reportedAt == null ? Instant.now() : reportedAt);
        if (source.equals(previous)) {
            return;
        }

        boolean normal = powerProperties.isNormal(source);
        log.warn("Device {} reports power source {} (was {})", device.getDeviceCode(), source, previous);
        realtimeEventPublisher.publish(RealtimeEventType.DEVICE_STATUS_CHANGED, toStatus(device));

        if (!normal) {
            alertService.raise(NewAlert.builder(AlertType.POWER_BACKUP, AlertSeverity.CRITICAL)
                    .title("Running on backup power")
                    .message("Device %s reports power source %s. Commands to actuator types blocked on backup power are refused."
                            .formatted(device.getDeviceCode(), source))
                    .source(AlertSource.DEVICE)
                    .device(device)
                    .metadata(Map.of("powerSource", source, "previousPowerSource", String.valueOf(previous)))
                    .build());
        } else if (previous != null && !powerProperties.isNormal(previous)) {
            alertService.raise(NewAlert.builder(AlertType.POWER_RESTORED, AlertSeverity.INFO)
                    .title("Normal power restored")
                    .message("Device %s reports power source %s again".formatted(device.getDeviceCode(), source))
                    .source(AlertSource.DEVICE)
                    .device(device)
                    .metadata(Map.of("powerSource", source, "previousPowerSource", previous))
                    .build());
        }
    }

    /** Whether any device reports a non-normal power source. Ignores liveness: an interlock must not relax on silence. */
    @Transactional(readOnly = true)
    public List<Device> devicesOnBackupPower() {
        return deviceRepository.findByPowerSourceIsNotNull().stream()
                .filter(this::isOnBackupPower)
                .toList();
    }

    /** Flips to OFFLINE with one alert per outage; reloads the row (caller's copy is detached) and rechecks staleness. */
    @Transactional
    public void markOffline(Device stale) {
        Device device = deviceRepository.findById(stale.getId()).orElse(null);
        if (device == null || device.getStatus() == DeviceStatus.OFFLINE) {
            return;
        }
        Instant cutoff = Instant.now().minus(deviceProperties.offlineTimeout());
        if (device.getLastSeenAt() != null && !device.getLastSeenAt().isBefore(cutoff)) {
            return;
        }
        device.setStatus(DeviceStatus.OFFLINE);
        log.warn(
                "Device {} marked OFFLINE; last seen at {}",
                device.getDeviceCode(),
                device.getLastSeenAt());
        realtimeEventPublisher.publish(RealtimeEventType.DEVICE_STATUS_CHANGED, toStatus(device));
        alertService.raiseOnceForDevice(NewAlert.builder(AlertType.DEVICE_OFFLINE, AlertSeverity.CRITICAL)
                .title("Device offline")
                .message("No message received from %s for more than %d seconds"
                        .formatted(device.getDeviceCode(), deviceProperties.offlineTimeout().toSeconds()))
                .source(AlertSource.SYSTEM)
                .device(device)
                .metadata(Map.of(
                        "lastSeenAt", String.valueOf(device.getLastSeenAt()),
                        "offlineTimeoutSeconds", deviceProperties.offlineTimeout().toSeconds()))
                .build());
    }

    /** Devices that have gone quiet for longer than the configured timeout. */
    @Transactional(readOnly = true)
    public List<Device> findStaleDevices(Instant now) {
        return deviceRepository.findByStatusAndLastSeenAtBefore(
                DeviceStatus.ONLINE, now.minus(deviceProperties.offlineTimeout()));
    }

    @Transactional(readOnly = true)
    public long countByStatus(DeviceStatus status) {
        return deviceRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return deviceRepository.count();
    }
}
