package com.fertimata.backend.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.calibration.ReadingCalibrator;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.config.TelemetryProperties;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {

    @Mock
    private SensorRepository sensorRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    @Mock
    private DeviceService deviceService;

    @Mock
    private TelemetryProperties telemetryProperties;

    @Mock
    private JsonSupport jsonSupport;

    @Mock
    private ReadingCalibrator readingCalibrator;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    private SensorService sensorService;

    private final Device device = TestFixtures.device(1L, "RIO-TANK-01");

    @BeforeEach
    void setUp() {
        sensorService = new SensorService(
                sensorRepository,
                sensorReadingRepository,
                deviceService,
                telemetryProperties,
                jsonSupport,
                readingCalibrator,
                realtimeEventPublisher);
    }

    @Test
    @DisplayName("renaming or disabling a sensor is pushed to other dashboards")
    void updatePublishesSensorUpdated() {
        Sensor sensor = TestFixtures.sensor(5L, device, "EC-01", "tank.ec", "uS/cm");
        when(sensorRepository.findById(5L)).thenReturn(Optional.of(sensor));

        SensorResponse response =
                sensorService.update(5L, new SensorUpdateRequest("EC tandon", null, null, null, false, null));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(realtimeEventPublisher).publish(eq(RealtimeEventType.SENSOR_UPDATED), payload.capture());
        assertThat(payload.getValue()).isEqualTo(response);
        assertThat(response.name()).isEqualTo("EC tandon");
        assertThat(response.enabled()).isFalse();
    }

    @Test
    @DisplayName("a sensor auto-registered from telemetry shows up for review without a reload")
    void autoRegistrationPublishesSensorUpdated() {
        when(sensorRepository.findByDeviceIdAndMetricKey(1L, "tank.orp")).thenReturn(Optional.empty());
        when(sensorRepository.findByDeviceIdAndCode(1L, "tank.orp")).thenReturn(Optional.empty());
        when(telemetryProperties.autoRegisterSensors()).thenReturn(true);
        when(sensorRepository.save(any(Sensor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Sensor> sensor = sensorService.resolveForMetric(device, "tank.orp");

        assertThat(sensor).isPresent();
        verify(realtimeEventPublisher).publish(eq(RealtimeEventType.SENSOR_UPDATED), any(SensorResponse.class));
    }
}
