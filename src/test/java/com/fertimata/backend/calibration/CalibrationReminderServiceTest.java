package com.fertimata.backend.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorRepository;
import com.fertimata.backend.support.TestFixtures;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
class CalibrationReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");

    @Mock
    private SensorRepository sensorRepository;

    @Mock
    private ActuatorRepository actuatorRepository;

    @Mock
    private SensorDriftCheckRepository driftCheckRepository;

    @Mock
    private ActuatorCalibrationRepository actuatorCalibrationRepository;

    @Mock
    private AlertService alertService;

    private CalibrationReminderService service;
    private Device device;

    @BeforeEach
    void setUp() {
        CalibrationProperties properties = new CalibrationProperties(
                null,
                null,
                new CalibrationProperties.DriftCheck(List.of("tank.ec"), Duration.ofDays(7)),
                new CalibrationProperties.Pump(List.of("DOSING_PUMP"), Duration.ofDays(30), 3),
                null);
        service = new CalibrationReminderService(
                sensorRepository,
                actuatorRepository,
                driftCheckRepository,
                actuatorCalibrationRepository,
                alertService,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        device = TestFixtures.device(1L, "RIO-TANK-01");
        when(alertService.raiseOnceForSensor(any())).thenReturn(true);
        when(alertService.raiseOnceForActuator(any())).thenReturn(true);
        when(actuatorRepository.findAllWithDevice()).thenReturn(List.of());
        when(sensorRepository.findAllWithDevice()).thenReturn(List.of());
    }

    @Test
    @DisplayName("a listed sensor that was never checked, or checked over a week ago, gets a reminder")
    void driftReminder() {
        Sensor ec = TestFixtures.sensor(10L, device, "TANK-EC", "tank.ec", "mS/cm");
        Sensor ph = TestFixtures.sensor(11L, device, "TANK-PH", "tank.ph", "pH");
        when(sensorRepository.findAllWithDevice()).thenReturn(List.of(ec, ph));
        when(driftCheckRepository.findFirstBySensorIdOrderByCheckedAtDesc(10L))
                .thenReturn(Optional.of(driftCheck(NOW.minus(Duration.ofDays(9)))));

        assertThat(service.raiseDueReminders()).isEqualTo(1);

        ArgumentCaptor<NewAlert> alert = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raiseOnceForSensor(alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(AlertType.DRIFT_CHECK_DUE);
        assertThat(alert.getValue().relatedSensor()).isSameAs(ec);
        assertThat(alert.getValue().message()).contains("9 hari");
    }

    @Test
    @DisplayName("a recent drift check and a recent pump calibration raise nothing")
    void recentIsQuiet() {
        Sensor ec = TestFixtures.sensor(10L, device, "TANK-EC", "tank.ec", "mS/cm");
        Actuator pump = new Actuator(device, "DOSING-N", "Pompa dosing N", "DOSING_PUMP");
        TestFixtures.setId(pump, 7L);
        when(sensorRepository.findAllWithDevice()).thenReturn(List.of(ec));
        when(actuatorRepository.findAllWithDevice()).thenReturn(List.of(pump));
        when(driftCheckRepository.findFirstBySensorIdOrderByCheckedAtDesc(10L))
                .thenReturn(Optional.of(driftCheck(NOW.minus(Duration.ofDays(2)))));
        ActuatorCalibration recent = new ActuatorCalibration();
        recent.setCalibratedAt(NOW.minus(Duration.ofDays(10)));
        when(actuatorCalibrationRepository.findFirstByActuatorIdOrderByCalibratedAtDesc(7L)).thenReturn(Optional.of(recent));

        assertThat(service.raiseDueReminders()).isZero();
        verify(alertService, never()).raiseOnceForSensor(any());
        verify(alertService, never()).raiseOnceForActuator(any());
    }

    @Test
    @DisplayName("an uncalibrated dosing pump gets a reminder; other actuator types do not")
    void pumpReminder() {
        Actuator pump = new Actuator(device, "DOSING-N", "Pompa dosing N", "DOSING_PUMP");
        TestFixtures.setId(pump, 7L);
        Actuator stirrer = new Actuator(device, "STIRRER", "Pompa aduk", "STIRRER");
        TestFixtures.setId(stirrer, 8L);
        when(actuatorRepository.findAllWithDevice()).thenReturn(List.of(pump, stirrer));
        when(actuatorCalibrationRepository.findFirstByActuatorIdOrderByCalibratedAtDesc(any())).thenReturn(Optional.empty());

        assertThat(service.raiseDueReminders()).isEqualTo(1);
        ArgumentCaptor<NewAlert> alert = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raiseOnceForActuator(alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(AlertType.PUMP_CALIBRATION_DUE);
        assertThat(alert.getValue().relatedActuator()).isSameAs(pump);
    }

    private static SensorDriftCheck driftCheck(Instant at) {
        SensorDriftCheck check = new SensorDriftCheck();
        check.setCheckedAt(at);
        return check;
    }
}
