package com.fertimata.backend.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.ActuatorService;
import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorCommand;
import com.fertimata.backend.actuator.domain.ActuatorCommandRepository;
import com.fertimata.backend.actuator.domain.CommandStatus;
import com.fertimata.backend.actuator.safety.ActuatorSafetyProperties;
import com.fertimata.backend.calibration.dto.DispensedVolumeResponse;
import com.fertimata.backend.calibration.dto.PumpCalibrationRequest;
import com.fertimata.backend.calibration.dto.PumpCalibrationResponse;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PumpCalibrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");

    @Mock
    private ActuatorService actuatorService;

    @Mock
    private ActuatorCalibrationRepository calibrationRepository;

    @Mock
    private ActuatorCommandRepository commandRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    @Mock
    private UserRepository userRepository;

    private PumpCalibrationService service;
    private Actuator pump;

    @BeforeEach
    void setUp() {
        ActuatorSafetyProperties safety =
                new ActuatorSafetyProperties(List.of("ON"), "durationSeconds", null, null, null, null, false, null, null, null);
        service = new PumpCalibrationService(
                actuatorService,
                calibrationRepository,
                commandRepository,
                sensorReadingRepository,
                userRepository,
                safety,
                new CalibrationProperties(null, null, null, null, null),
                new JsonSupport(new ObjectMapper()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        Device device = TestFixtures.device(1L, "RIO-TANK-01");
        pump = new Actuator(device, "DOSING-N", "Pompa dosing N", "DOSING_PUMP");
        TestFixtures.setId(pump, 7L);
        when(actuatorService.require(7L)).thenReturn(pump);
        when(calibrationRepository.save(any(ActuatorCalibration.class))).thenAnswer(inv -> {
            ActuatorCalibration c = inv.getArgument(0);
            TestFixtures.setId(c, 50L);
            return c;
        });
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(sensorReadingRepository.findFirstByMetricKeyAndRecordedAtLessThanEqualOrderByRecordedAtDesc(anyString(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("flow is total volume over total time, with the spread between trials as CV")
    void pooledFlowFromTrials() {
        when(calibrationRepository.findByActuatorIdOrderByCalibratedAtDesc(7L)).thenAnswer(inv -> List.of());

        PumpCalibrationResponse created = service.create(7L, new PumpCalibrationRequest(
                null,
                List.of(trial("30", "60"), trial("30", "63"), trial("60", "117")),
                null, null, null, "selang baru"), "operator");

        // 240 mL / 120 s
        assertThat(created.flowMlPerSecond()).isEqualByComparingTo("2.0");
        assertThat(created.trialCount()).isEqualTo(3);
        assertThat(created.flowCvPercent()).isNotNull().isPositive();
        assertThat(created.trials()).hasSize(3);
    }

    @Test
    @DisplayName("fewer trials than Protokol §8 asks for are refused")
    void minimumTrials() {
        assertThatThrownBy(() -> service.create(7L, new PumpCalibrationRequest(
                        null, List.of(trial("30", "60")), null, null, null, null), "operator"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("At least 3 trials");
    }

    @Test
    @DisplayName("only pump types can be flow-calibrated")
    void onlyPumps() {
        Actuator stirrer = new Actuator(pump.getDevice(), "STIRRER", "Pompa aduk", "STIRRER");
        TestFixtures.setId(stirrer, 8L);
        when(actuatorService.require(8L)).thenReturn(stirrer);

        assertThatThrownBy(() -> service.create(8L, new PumpCalibrationRequest(
                        null, null, new BigDecimal("2"), 3, null, null), "operator"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not a calibrated pump");
    }

    @Test
    @DisplayName("dispensed volume uses the flow valid at each run and cuts a run short at the next command")
    void dispensedVolume() {
        Instant from = NOW.minusSeconds(86_400);
        ActuatorCalibration older = calibration(1L, "1.0", from.plusSeconds(10));
        ActuatorCalibration newer = calibration(2L, "2.0", from.plusSeconds(5_000));
        when(calibrationRepository.findByActuatorIdOrderByCalibratedAtDesc(7L)).thenReturn(List.of(newer, older));
        when(commandRepository.findByActuatorIdAndStatusAndExecutedAtBetweenOrderByExecutedAtAsc(
                        eq(7L), eq(CommandStatus.EXECUTED), any(), any()))
                .thenReturn(List.of(
                        command("ON", "{\"durationSeconds\": 30}", from.plusSeconds(5)), // sebelum kalibrasi pertama
                        command("ON", "{\"durationSeconds\": 60}", from.plusSeconds(1_000)), // 60 s x 1 mL/s
                        command("ON", "{\"durationSeconds\": 60}", from.plusSeconds(6_000)), // dipotong OFF setelah 20 s
                        command("OFF", null, from.plusSeconds(6_020)),
                        command("ON", null, from.plusSeconds(7_000))));

        DispensedVolumeResponse result = service.dispensed(7L, from, NOW);

        assertThat(result.runCount()).isEqualTo(4);
        assertThat(result.totalSeconds()).isEqualByComparingTo("110");
        assertThat(result.volumeMl()).isEqualByComparingTo("100"); // 60 x 1 + 20 x 2
        assertThat(result.uncalibratedRunCount()).isEqualTo(1);
        assertThat(result.unknownDurationRunCount()).isEqualTo(1);
        assertThat(result.currentFlowMlPerSecond()).isEqualByComparingTo("2.0");
    }

    private ActuatorCalibration calibration(Long id, String flow, Instant at) {
        ActuatorCalibration c = new ActuatorCalibration();
        TestFixtures.setId(c, id);
        c.setActuator(pump);
        c.setFlowMlPerSecond(new BigDecimal(flow));
        c.setCalibratedAt(at);
        c.setTrialCount(3);
        return c;
    }

    private ActuatorCommand command(String type, String parameters, Instant executedAt) {
        ActuatorCommand c = new ActuatorCommand();
        c.setActuator(pump);
        c.setDevice(pump.getDevice());
        c.setCommandType(type);
        c.setParameters(parameters);
        c.setRequestedAt(executedAt);
        c.markExecuted(executedAt);
        return c;
    }

    private static PumpCalibrationRequest.Trial trial(String seconds, String ml) {
        return new PumpCalibrationRequest.Trial(new BigDecimal(seconds), new BigDecimal(ml));
    }
}
