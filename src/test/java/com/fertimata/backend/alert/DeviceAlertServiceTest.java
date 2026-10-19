package com.fertimata.backend.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.mqtt.payload.DeviceAlertPayload;
import com.fertimata.backend.sensor.SensorRepository;
import com.fertimata.backend.support.TestFixtures;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceAlertServiceTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private SensorRepository sensorRepository;

    @Mock
    private ActuatorRepository actuatorRepository;

    @Mock
    private AlertService alertService;

    private DeviceAlertService service;
    private Device panel;

    @BeforeEach
    void setUp() {
        service = new DeviceAlertService(deviceService, sensorRepository, actuatorRepository, alertService);
        panel = TestFixtures.onlineDevice(2L, "PANEL-01");
        when(deviceService.resolveForIngest("PANEL-01")).thenReturn(Optional.of(panel));
    }

    @Test
    @DisplayName("a device alert keeps its own type and severity and is linked to the actuator it names")
    void storesDeviceVocabulary() {
        Actuator pump = TestFixtures.actuator(7L, panel, "DIST-PUMP");
        when(actuatorRepository.findByDeviceIdAndCode(2L, "DIST-PUMP")).thenReturn(Optional.of(pump));

        service.record("PANEL-01", new DeviceAlertPayload(
                "SSR_SHORT", "critical", "SSR pompa short", "Arus 1.4 A saat R0 OFF", null, "DIST-PUMP", null,
                Map.of("current", 1.4)));

        ArgumentCaptor<NewAlert> captor = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raiseOnceForDevice(captor.capture());
        NewAlert alert = captor.getValue();
        assertThat(alert.type()).isEqualTo("SSR_SHORT");
        assertThat(alert.severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.source()).isEqualTo(AlertSource.DEVICE);
        assertThat(alert.relatedActuator()).isSameAs(pump);
        assertThat(alert.metadata()).containsKey("current").containsKey("reportedAt");
        verify(deviceService).recordContact(eq(panel), any(), any());
    }

    @Test
    @DisplayName("missing fields fall back to safe defaults instead of dropping the alert")
    void defaultsForSparsePayload() {
        service.record("PANEL-01", new DeviceAlertPayload(null, "LOUD", null, null, null, null, null, null));

        ArgumentCaptor<NewAlert> captor = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raiseOnceForDevice(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AlertType.DEVICE_REPORTED);
        assertThat(captor.getValue().severity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(captor.getValue().message()).contains("PANEL-01");
    }

    @Test
    @DisplayName("an alert from an unknown device is not stored")
    void unknownDeviceIgnored() {
        when(deviceService.resolveForIngest(anyString())).thenReturn(Optional.empty());

        service.record("GHOST", new DeviceAlertPayload("X", null, null, null, null, null, null, null));

        verify(alertService, never()).raiseOnceForDevice(any());
    }
}
