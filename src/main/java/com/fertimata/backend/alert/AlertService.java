package com.fertimata.backend.alert;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.user.User;
import com.fertimata.backend.user.UserRepository;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Raises, lists and acknowledges alerts. Infrastructure only: no agronomic threshold is agreed, so none is invented. */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final JsonSupport jsonSupport;
    private final ApplicationEventPublisher eventPublisher;

    public AlertService(
            AlertRepository alertRepository,
            UserRepository userRepository,
            RealtimeEventPublisher realtimeEventPublisher,
            JsonSupport jsonSupport,
            ApplicationEventPublisher eventPublisher) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.jsonSupport = jsonSupport;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AlertResponse raise(NewAlert request) {
        Alert alert = new Alert();
        alert.setType(request.type());
        alert.setSeverity(request.severity());
        alert.setTitle(request.title());
        alert.setMessage(request.message());
        alert.setSource(request.source());
        alert.setRelatedDevice(request.relatedDevice());
        alert.setRelatedSensor(request.relatedSensor());
        alert.setRelatedActuator(request.relatedActuator());
        alert.setMetadata(jsonSupport.toJson(request.metadata()));

        Alert saved = alertRepository.save(alert);
        log.info("Alert raised: type={} severity={} id={}", saved.getType(), saved.getSeverity(), saved.getId());

        AlertResponse response = AlertResponse.from(saved);
        realtimeEventPublisher.publish(RealtimeEventType.ALERT_CREATED, response);
        // Notifikasi push dikirim setelah commit, di luar transaksi ini (lihat PushNotificationService).
        eventPublisher.publishEvent(new AlertRaisedEvent(response));
        return response;
    }

    /** Raises only if no unacknowledged alert of this type is open for the device, so an outage does not alert every minute. */
    @Transactional
    public void raiseOnceForDevice(NewAlert request) {
        Long deviceId = request.relatedDevice() == null ? null : request.relatedDevice().getId();
        if (deviceId != null
                && alertRepository.existsByTypeAndRelatedDeviceIdAndAcknowledgedFalse(request.type(), deviceId)) {
            log.debug("Suppressing duplicate {} alert for device id={}", request.type(), deviceId);
            return;
        }
        raise(request);
    }

    /** Seperti {@link #raiseOnceForDevice}, tetapi per sensor (mis. pengingat uji drift). */
    @Transactional
    public boolean raiseOnceForSensor(NewAlert request) {
        Long sensorId = request.relatedSensor() == null ? null : request.relatedSensor().getId();
        if (sensorId != null
                && alertRepository.existsByTypeAndRelatedSensorIdAndAcknowledgedFalse(request.type(), sensorId)) {
            return false;
        }
        raise(request);
        return true;
    }

    /** Seperti {@link #raiseOnceForDevice}, tetapi per aktuator (mis. pengingat kalibrasi pompa). */
    @Transactional
    public boolean raiseOnceForActuator(NewAlert request) {
        Long actuatorId = request.relatedActuator() == null ? null : request.relatedActuator().getId();
        if (actuatorId != null
                && alertRepository.existsByTypeAndRelatedActuatorIdAndAcknowledgedFalse(request.type(), actuatorId)) {
            return false;
        }
        raise(request);
        return true;
    }

    /** Alert terbaru dulu; filter perangkat/sensor/aktuator opsional dan bisa digabung (untuk halaman detail). */
    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> list(AlertFilter filter, Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> page = alertRepository.findAll(AlertSpecifications.matching(filter), sorted);
        return PageResponse.from(page, AlertResponse::from);
    }

    @Transactional(readOnly = true)
    public AlertResponse get(Long id) {
        return alertRepository
                .findById(id)
                .map(AlertResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> active(int limit) {
        return alertRepository.findActive(PageRequest.of(0, limit)).stream()
                .map(AlertResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unacknowledgedCount() {
        return alertRepository.countByAcknowledgedFalse();
    }

    @Transactional(readOnly = true)
    public long unacknowledgedCount(AlertSeverity severity) {
        return alertRepository.countByAcknowledgedFalseAndSeverity(severity);
    }

    /** Marks an alert as seen; throws BusinessRuleException if already acknowledged, so the first acknowledger is kept. */
    @Transactional
    public AlertResponse acknowledge(Long id, String username) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alert", id));
        if (alert.isAcknowledged()) {
            throw new BusinessRuleException("Alert %d has already been acknowledged".formatted(id));
        }
        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        alert.acknowledge(user, Instant.now());
        log.info("Alert {} acknowledged by {}", id, username);
        AlertResponse response = AlertResponse.from(alert);
        // Operator lain yang sedang membuka dashboard harus melihat alert ini hilang tanpa reload.
        realtimeEventPublisher.publish(RealtimeEventType.ALERT_UPDATED, response);
        return response;
    }
}
