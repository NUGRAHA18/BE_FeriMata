package com.fertimata.backend.dashboard;

import com.fertimata.backend.actuator.ActuatorCommandService;
import com.fertimata.backend.actuator.ActuatorService;
import com.fertimata.backend.actuator.dto.ActuatorCommandResponse;
import com.fertimata.backend.ai.AiDetectionService;
import com.fertimata.backend.alert.AlertResponse;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.config.AppProperties;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.device.DeviceRepository;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.device.DeviceStatus;
import com.fertimata.backend.device.DeviceStatusResponse;
import com.fertimata.backend.sensor.SensorService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the dashboard overview.
 *
 * <p>The aggregation lives here rather than in the controller, and it reuses the domain services
 * rather than reaching into repositories, so the numbers on the dashboard cannot drift from the
 * numbers on the individual endpoints.
 */
@Service
public class DashboardService {

    private static final int ACTIVE_ALERT_LIMIT = 10;
    private static final int ACTIVITY_LIMIT = 15;

    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final SensorService sensorService;
    private final ActuatorService actuatorService;
    private final ActuatorCommandService commandService;
    private final AlertService alertService;
    private final AiDetectionService aiDetectionService;
    private final CultivationService cultivationService;
    private final AppProperties appProperties;

    public DashboardService(
            DeviceService deviceService,
            DeviceRepository deviceRepository,
            SensorService sensorService,
            ActuatorService actuatorService,
            ActuatorCommandService commandService,
            AlertService alertService,
            AiDetectionService aiDetectionService,
            CultivationService cultivationService,
            AppProperties appProperties) {
        this.deviceService = deviceService;
        this.deviceRepository = deviceRepository;
        this.sensorService = sensorService;
        this.actuatorService = actuatorService;
        this.commandService = commandService;
        this.alertService = alertService;
        this.aiDetectionService = aiDetectionService;
        this.cultivationService = cultivationService;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview() {
        List<DeviceStatusResponse> deviceStatuses = deviceRepository.findAllByOrderByDeviceCodeAsc().stream()
                .map(deviceService::toStatus)
                .toList();

        DashboardOverviewResponse.DeviceSection devices = new DashboardOverviewResponse.DeviceSection(
                deviceStatuses.size(),
                count(deviceStatuses, DeviceStatus.ONLINE),
                count(deviceStatuses, DeviceStatus.OFFLINE),
                count(deviceStatuses, DeviceStatus.UNKNOWN),
                deviceStatuses);

        DashboardOverviewResponse.SensorSection sensors = new DashboardOverviewResponse.SensorSection(
                sensorService.countAll(), sensorService.countEnabled(), sensorService.latestPerSensor());

        List<AlertResponse> activeAlerts = alertService.active(ACTIVE_ALERT_LIMIT);
        DashboardOverviewResponse.AlertSection alerts = new DashboardOverviewResponse.AlertSection(
                alertService.unacknowledgedCount(),
                alertService.unacknowledgedCount(AlertSeverity.CRITICAL),
                alertService.unacknowledgedCount(AlertSeverity.WARNING),
                activeAlerts);

        DashboardOverviewResponse.SystemSection system = new DashboardOverviewResponse.SystemSection(
                appProperties.mode().name(),
                commandService.transportDescription(),
                commandService.transportAvailable(),
                Instant.now());

        return new DashboardOverviewResponse(
                system,
                devices,
                sensors,
                actuatorService.allStatuses(),
                alerts,
                aiDetectionService.latest().orElse(null),
                recentActivity(activeAlerts),
                cultivation());
    }

    private DashboardOverviewResponse.CultivationSection cultivation() {
        return cultivationService
                .activeStatus()
                .map(s -> new DashboardOverviewResponse.CultivationSection(
                        s.cycle().id(),
                        s.cycle().code(),
                        s.cycle().name(),
                        s.cycle().transplantDate(),
                        s.hst(),
                        s.currentPhase(),
                        s.nextPhase(),
                        s.daysUntilNextPhase(),
                        s.plantCount(),
                        s.treatmentPlantCount()))
                .orElse(null);
    }

    /**
     * A merged feed of the last commands and alerts.
     *
     * <p>Two sources are enough to answer "what just happened here?" without turning the dashboard
     * into an audit log; the dedicated endpoints stay the place to dig further.
     */
    private List<DashboardOverviewResponse.ActivityEntry> recentActivity(List<AlertResponse> activeAlerts) {
        List<DashboardOverviewResponse.ActivityEntry> entries = new ArrayList<>();

        for (ActuatorCommandResponse command :
                commandService.recent(PageRequest.of(0, ACTIVITY_LIMIT)).content()) {
            String actor = command.requestedBy() == null ? command.source().name() : command.requestedBy();
            entries.add(new DashboardOverviewResponse.ActivityEntry(
                    "ACTUATOR_COMMAND",
                    command.requestedAt(),
                    "%s %s on %s".formatted(command.commandType(), command.actuatorCode(), command.deviceCode()),
                    "%s, requested by %s".formatted(command.status(), actor),
                    command.status().name(),
                    command.commandType(),
                    command.actuatorCode(),
                    command.deviceCode(),
                    actor,
                    command.id()));
        }

        for (AlertResponse alert : activeAlerts) {
            entries.add(new DashboardOverviewResponse.ActivityEntry(
                    "ALERT",
                    alert.createdAt(),
                    alert.title(),
                    alert.severity() + ": " + alert.message(),
                    alert.severity().name(),
                    null,
                    alert.relatedActuatorCode(),
                    alert.relatedDeviceCode(),
                    alert.source().name(),
                    alert.id()));
        }

        return entries.stream()
                .sorted(Comparator.comparing(
                                DashboardOverviewResponse.ActivityEntry::at,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .limit(ACTIVITY_LIMIT)
                .toList();
    }

    private long count(List<DeviceStatusResponse> statuses, DeviceStatus status) {
        return statuses.stream().filter(item -> item.status() == status).count();
    }
}
