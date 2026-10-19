package com.fertimata.backend.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.config.DeviceProperties;
import com.fertimata.backend.config.PowerProperties;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Mock
    private AlertService alertService;

    @Mock
    private JsonSupport jsonSupport;

    private static final PowerProperties POWER = new PowerProperties(java.util.List.of("MAINS"));

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        DeviceProperties properties = new DeviceProperties(Duration.ofSeconds(120), Duration.ofSeconds(30), true);
        deviceService = new DeviceService(
                deviceRepository, properties, POWER, realtimeEventPublisher, alertService, jsonSupport);
    }

    @Test
    @DisplayName("registering a device stores it and returns the read model")
    void registerDevice() {
        when(deviceRepository.existsByDeviceCode("JETSON-001")).thenReturn(false);
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device saved = invocation.getArgument(0);
            TestFixtures.setId(saved, 1L);
            return saved;
        });

        DeviceResponse response = deviceService.register(
                new DeviceRegistrationRequest("JETSON-001", "Greenhouse edge device", "EDGE_GATEWAY", null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.deviceCode()).isEqualTo("JETSON-001");
        assertThat(response.status()).isEqualTo(DeviceStatus.UNKNOWN);
    }

    @Test
    @DisplayName("a duplicate device code is rejected instead of creating a second row")
    void duplicateDeviceCodeIsRejected() {
        when(deviceRepository.existsByDeviceCode("JETSON-001")).thenReturn(true);

        assertThatThrownBy(() -> deviceService.register(
                        new DeviceRegistrationRequest("JETSON-001", "Duplicate", null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already registered");

        verify(deviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("a heartbeat updates lastSeenAt and brings an unknown device online")
    void heartbeatUpdatesLastSeen() {
        Device device = TestFixtures.device(1L, "JETSON-001");
        Instant seenAt = Instant.parse("2026-09-15T04:00:00Z");

        deviceService.recordContact(device, seenAt, null);

        assertThat(device.getLastSeenAt()).isEqualTo(seenAt);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        verify(realtimeEventPublisher).publish(eqType(RealtimeEventType.DEVICE_STATUS_CHANGED), any());
    }

    @Test
    @DisplayName("a device coming back from OFFLINE raises a recovery alert exactly once")
    void recoveryRaisesAlert() {
        Device device = TestFixtures.device(1L, "JETSON-001");
        device.setStatus(DeviceStatus.OFFLINE);

        deviceService.recordContact(device, Instant.now(), null);

        ArgumentCaptor<NewAlert> captor = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raise(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AlertType.DEVICE_RECOVERED);

        // A second heartbeat is not news and must not raise another alert.
        deviceService.recordContact(device, Instant.now(), null);
        verify(alertService).raise(any());
    }

    @Test
    @DisplayName("a heartbeat from an already-online device does not spam status events")
    void repeatedHeartbeatIsQuiet() {
        Device device = TestFixtures.onlineDevice(1L, "JETSON-001");

        deviceService.recordContact(device, Instant.now(), null);

        verify(realtimeEventPublisher, never()).publish(any(), any());
        verify(alertService, never()).raise(any());
    }

    @Test
    @DisplayName("marking a device offline publishes a status change and raises one alert")
    void markOffline() {
        Device device = TestFixtures.onlineDevice(1L, "JETSON-001");
        device.markSeen(Instant.now().minusSeconds(600));

        deviceService.markOffline(device);

        assertThat(device.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
        verify(realtimeEventPublisher).publish(eqType(RealtimeEventType.DEVICE_STATUS_CHANGED), any());
        ArgumentCaptor<NewAlert> captor = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raiseOnceForDevice(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AlertType.DEVICE_OFFLINE);
    }

    @Test
    @DisplayName("an unknown device code is auto-registered when the policy allows it")
    void autoRegistersUnknownDevice() {
        when(deviceRepository.findByDeviceCode("NEW-DEVICE")).thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Device> resolved = deviceService.resolveForIngest("NEW-DEVICE");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getDeviceCode()).isEqualTo("NEW-DEVICE");
    }

    @Test
    @DisplayName("an unknown device code is not created when auto-registration is off")
    void doesNotAutoRegisterWhenDisabled() {
        DeviceProperties strict = new DeviceProperties(Duration.ofSeconds(120), Duration.ofSeconds(30), false);
        DeviceService strictService =
                new DeviceService(deviceRepository, strict, POWER, realtimeEventPublisher, alertService, jsonSupport);
        when(deviceRepository.findByDeviceCode("NEW-DEVICE")).thenReturn(Optional.empty());

        assertThat(strictService.resolveForIngest("NEW-DEVICE")).isEmpty();
        verify(deviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("switching to battery raises one critical alert and a status event; repeats are quiet")
    void batteryReportRaisesAlertOnce() {
        Device device = TestFixtures.onlineDevice(1L, "JETSON-01");
        device.reportPowerSource("MAINS", Instant.now());

        deviceService.reportPowerSource(device, "battery", Instant.now());

        assertThat(device.getPowerSource()).isEqualTo("BATTERY");
        ArgumentCaptor<NewAlert> captor = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raise(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AlertType.POWER_BACKUP);
        verify(realtimeEventPublisher).publish(eqType(RealtimeEventType.DEVICE_STATUS_CHANGED), any());
        assertThat(deviceService.toStatus(device).onBackupPower()).isTrue();

        deviceService.reportPowerSource(device, "BATTERY", Instant.now());
        verify(alertService).raise(any());
    }

    @Test
    @DisplayName("returning to mains after battery raises a restore alert")
    void mainsAfterBatteryRaisesRestoreAlert() {
        Device device = TestFixtures.onlineDevice(1L, "JETSON-01");
        device.reportPowerSource("BATTERY", Instant.now());

        deviceService.reportPowerSource(device, "MAINS", Instant.now());

        ArgumentCaptor<NewAlert> captor = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raise(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AlertType.POWER_RESTORED);
    }

    @Test
    @DisplayName("an unknown power source counts as backup power, which is the safe reading")
    void unknownSourceCountsAsBackup() {
        Device reporter = TestFixtures.onlineDevice(1L, "JETSON-01");
        reporter.reportPowerSource("UNKNOWN", Instant.now());
        Device mains = TestFixtures.onlineDevice(2L, "PZEM-01");
        mains.reportPowerSource("MAINS", Instant.now());
        when(deviceRepository.findByPowerSourceIsNotNull()).thenReturn(java.util.List.of(reporter, mains));

        assertThat(deviceService.devicesOnBackupPower()).containsExactly(reporter);
    }

    private static RealtimeEventType eqType(RealtimeEventType type) {
        return org.mockito.ArgumentMatchers.eq(type);
    }
}
