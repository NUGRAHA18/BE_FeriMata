package com.fertimata.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.ActuatorCommandService;
import com.fertimata.backend.actuator.ActuatorService;
import com.fertimata.backend.ai.AiDetectionService;
import com.fertimata.backend.alert.AlertResponse;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.alert.AlertSource;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.config.AppProperties;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceRepository;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.device.DeviceStatus;
import com.fertimata.backend.device.DeviceStatusResponse;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.support.TestFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private SensorService sensorService;

    @Mock
    private ActuatorService actuatorService;

    @Mock
    private ActuatorCommandService commandService;

    @Mock
    private AlertService alertService;

    @Mock
    private AiDetectionService aiDetectionService;

    @Mock
    private CultivationService cultivationService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                deviceService,
                deviceRepository,
                sensorService,
                actuatorService,
                commandService,
                alertService,
                aiDetectionService,
                cultivationService,
                new AppProperties(AppProperties.Mode.DEVELOPMENT));
    }

    @Test
    @DisplayName("the overview aggregates every section in a single call")
    void aggregatesOverview() {
        Device online = TestFixtures.onlineDevice(1L, "JETSON-001");
        Device offline = TestFixtures.device(2L, "JETSON-002");
        offline.setStatus(DeviceStatus.OFFLINE);
        Device unknown = TestFixtures.device(3L, "JETSON-003");

        when(deviceRepository.findAllByOrderByDeviceCodeAsc()).thenReturn(List.of(online, offline, unknown));
        when(deviceService.toStatus(any(Device.class))).thenAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            return new DeviceStatusResponse(
                    device.getId(), device.getDeviceCode(), device.getStatus(), device.getLastSeenAt(), 5L, 120L,
                    null, null, false);
        });

        when(sensorService.countAll()).thenReturn(4L);
        when(sensorService.countEnabled()).thenReturn(3L);
        when(sensorService.latestPerSensor()).thenReturn(List.of());
        when(actuatorService.allStatuses()).thenReturn(List.of());
        when(commandService.recent(any())).thenReturn(new PageResponse<>(List.of(), 0, 15, 0, 0, true));
        when(commandService.transportDescription()).thenReturn("MOCK (in-memory loopback)");
        when(commandService.transportAvailable()).thenReturn(true);
        when(alertService.active(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(alert()));
        when(alertService.unacknowledgedCount()).thenReturn(1L);
        when(alertService.unacknowledgedCount(AlertSeverity.CRITICAL)).thenReturn(1L);
        when(alertService.unacknowledgedCount(AlertSeverity.WARNING)).thenReturn(0L);
        when(aiDetectionService.latest()).thenReturn(Optional.empty());
        when(cultivationService.activeStatus()).thenReturn(Optional.empty());

        DashboardOverviewResponse overview = dashboardService.overview();

        assertThat(overview.devices().total()).isEqualTo(3);
        assertThat(overview.devices().online()).isEqualTo(1);
        assertThat(overview.devices().offline()).isEqualTo(1);
        assertThat(overview.devices().unknown()).isEqualTo(1);

        assertThat(overview.sensors().total()).isEqualTo(4);
        assertThat(overview.sensors().enabled()).isEqualTo(3);

        assertThat(overview.alerts().unacknowledged()).isEqualTo(1);
        assertThat(overview.alerts().critical()).isEqualTo(1);
        assertThat(overview.alerts().active()).hasSize(1);

        assertThat(overview.system().mode()).isEqualTo("DEVELOPMENT");
        assertThat(overview.system().messagingTransport()).isEqualTo("MOCK (in-memory loopback)");
        assertThat(overview.system().messagingConnected()).isTrue();

        assertThat(overview.latestAiDetection()).isNull();
        assertThat(overview.cultivation()).isNull();
        assertThat(overview.recentActivity()).hasSize(1);
        assertThat(overview.recentActivity().get(0).kind()).isEqualTo("ALERT");
    }

    private AlertResponse alert() {
        return new AlertResponse(
                9L,
                AlertType.DEVICE_OFFLINE,
                AlertSeverity.CRITICAL,
                "Device offline",
                "No message received",
                AlertSource.SYSTEM,
                2L,
                "JETSON-002",
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                Instant.now());
    }
}
