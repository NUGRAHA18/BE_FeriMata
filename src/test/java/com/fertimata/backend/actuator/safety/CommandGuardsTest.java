package com.fertimata.backend.actuator.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.actuator.domain.OutboundCommand;
import com.fertimata.backend.common.exception.InvalidCommandException;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceRepository;
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.device.DeviceStatus;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorRepository;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Pins every interlock rule, including the universal one: a stop is never blocked. */
class CommandGuardsTest {

    /** Mirrors the FERTIMATA Rev A policy in hardware/fertimata-rev-a.yml. */
    private static final ActuatorSafetyProperties REV_A = new ActuatorSafetyProperties(
            List.of("ON"),
            "durationSeconds",
            List.of("DOSING_PUMP", "TROLLEY_MOTOR"),
            List.of("DOSING_PUMP", "TROLLEY_MOTOR"),
            List.of(List.of("DOSING_PUMP", "TROLLEY_MOTOR"), List.of("DOSING_PUMP", "DISTRIBUTION_PUMP")),
            List.of("ON"),
            true,
            Duration.ofSeconds(30),
            List.of(new ActuatorSafetyProperties.BlockedWhileActive(List.of("CAMERA_PAN"), List.of("TROLLEY_MOTOR"))),
            List.of(new ActuatorSafetyProperties.SignalInterlock(
                    "FLOAT_LOW", "RIO-TANK-01", "tank.float_low_ok", List.of("1", "CLOSED"), Duration.ofMinutes(10),
                    List.of("DISTRIBUTION_PUMP", "DOSING_PUMP"))));

    private final Device tank = TestFixtures.onlineDevice(1L, "RIO-TANK-01");
    private final Device panel = TestFixtures.onlineDevice(2L, "PANEL-01");

    private Actuator actuator(Long id, Device device, String code, String type) {
        Actuator actuator = TestFixtures.actuator(id, device, code);
        actuator.setType(type);
        return actuator;
    }

    private static CommandContext on(Actuator actuator, Map<String, Object> parameters) {
        return new CommandContext(actuator, "ON", parameters, REV_A.isActivating("ON"));
    }

    private static CommandContext off(Actuator actuator) {
        return new CommandContext(actuator, "OFF", Map.of(), REV_A.isActivating("OFF"));
    }

    @Nested
    class RunDuration {

        private final RunDurationGuard guard = new RunDurationGuard(REV_A);

        @Test
        @DisplayName("a timed type cannot be switched on without a run time")
        void timedTypeRequiresDuration() {
            Actuator pump = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");

            assertThatThrownBy(() -> guard.check(on(pump, Map.of())))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("durationSeconds");
        }

        @Test
        @DisplayName("the per-actuator limit is enforced and whole seconds are required")
        void limitAndWholeSeconds() {
            Actuator trolley = actuator(20L, panel, "TROLLEY-RUN", "TROLLEY_MOTOR");
            trolley.setMaxRunSeconds(120);

            assertThatCode(() -> guard.check(on(trolley, Map.of("durationSeconds", 90)))).doesNotThrowAnyException();
            assertThatThrownBy(() -> guard.check(on(trolley, Map.of("durationSeconds", 121))))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("120 second limit");
            assertThatThrownBy(() -> guard.check(on(trolley, Map.of("durationSeconds", 2.5))))
                    .isInstanceOf(InvalidCommandException.class);
            assertThatThrownBy(() -> guard.check(on(trolley, Map.of("durationSeconds", 0))))
                    .isInstanceOf(InvalidCommandException.class);
        }

        @Test
        @DisplayName("an untimed type without a limit may still be switched on open-ended")
        void untimedTypeIsFree() {
            Actuator pan = actuator(30L, panel, "CAMERA-PAN", "CAMERA_PAN");

            assertThatCode(() -> guard.check(on(pan, Map.of()))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a stop command never needs a run time")
        void stopIsNeverBlocked() {
            Actuator pump = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");
            pump.setMaxRunSeconds(10);

            assertThatCode(() -> guard.check(off(pump))).doesNotThrowAnyException();
        }
    }

    @Nested
    class BackupPower {

        private final DeviceService deviceService = mock(DeviceService.class);
        private final BackupPowerGuard guard = new BackupPowerGuard(REV_A, deviceService);

        @Test
        @DisplayName("dosing is refused while any device reports battery")
        void dosingBlockedOnBattery() {
            Device jetson = TestFixtures.onlineDevice(9L, "JETSON-01");
            jetson.reportPowerSource("BATTERY", Instant.now());
            when(deviceService.devicesOnBackupPower()).thenReturn(List.of(jetson));
            Actuator pump = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");

            assertThatThrownBy(() -> guard.check(on(pump, Map.of("durationSeconds", 5))))
                    .isInstanceOf(SafetyInterlockException.class)
                    .hasMessageContaining("backup power");
        }

        @Test
        @DisplayName("types not listed, and stop commands, are not affected by an outage")
        void otherTypesAndStopsPass() {
            Actuator pan = actuator(30L, panel, "CAMERA-PAN", "CAMERA_PAN");
            Actuator pump = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");

            assertThatCode(() -> guard.check(on(pan, Map.of()))).doesNotThrowAnyException();
            assertThatCode(() -> guard.check(off(pump))).doesNotThrowAnyException();
            verify(deviceService, never()).devicesOnBackupPower();
        }
    }

    @Nested
    class ExclusiveOperation {

        private final ActuatorRepository repository = mock(ActuatorRepository.class);
        private final ExclusiveOperationGuard guard = new ExclusiveOperationGuard(REV_A, repository);

        @Test
        @DisplayName("dosing cannot start while the trolley reports ON")
        void dosingBlockedByRunningTrolley() {
            Actuator pump = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");
            Actuator trolley = actuator(20L, panel, "TROLLEY-RUN", "TROLLEY_MOTOR");
            trolley.reportState("ON", Instant.now());
            when(repository.findAllWithDevice()).thenReturn(List.of(pump, trolley));

            assertThatThrownBy(() -> guard.check(on(pump, Map.of("durationSeconds", 5))))
                    .isInstanceOf(SafetyInterlockException.class)
                    .hasMessageContaining("TROLLEY-RUN");
        }

        @Test
        @DisplayName("actuators of the same type, or not in a shared group, do not exclude each other")
        void sameTypeAndUngroupedPass() {
            Actuator n = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");
            Actuator p = actuator(11L, tank, "DOSING-P", "DOSING_PUMP");
            Actuator trolley = actuator(20L, panel, "TROLLEY-RUN", "TROLLEY_MOTOR");
            Actuator pan = actuator(30L, panel, "CAMERA-PAN", "CAMERA_PAN");
            when(repository.findAllWithDevice()).thenReturn(List.of(n, p, trolley, pan));

            // Trolley and pan share no group: moving with the camera facing west is allowed.
            pan.reportState("ON", Instant.now());
            assertThatCode(() -> guard.check(on(trolley, Map.of("durationSeconds", 60))))
                    .doesNotThrowAnyException();

            // Two dosing pumps are the same type: sequencing them is the edge agent's call.
            p.reportState("ON", Instant.now());
            assertThatCode(() -> guard.check(on(n, Map.of("durationSeconds", 5)))).doesNotThrowAnyException();

            // The group is symmetric: a running dosing pump also holds the trolley.
            assertThatThrownBy(() -> guard.check(on(trolley, Map.of("durationSeconds", 60))))
                    .isInstanceOf(SafetyInterlockException.class);
        }
    }

    @Nested
    class BlockedWhileActive {

        private final ActuatorRepository repository = mock(ActuatorRepository.class);
        private final BlockedWhileActiveGuard guard = new BlockedWhileActiveGuard(REV_A, repository);

        @Test
        @DisplayName("the camera cannot pan while the trolley runs, but the trolley may run with the camera west")
        void panBlockedOnlyOneWay() {
            Actuator pan = actuator(30L, panel, "CAMERA-PAN", "CAMERA_PAN");
            Actuator trolley = actuator(20L, panel, "TROLLEY-RUN", "TROLLEY_MOTOR");
            when(repository.findAllWithDevice()).thenReturn(List.of(pan, trolley));

            trolley.reportState("ON", Instant.now());
            assertThatThrownBy(() -> guard.check(on(pan, Map.of())))
                    .isInstanceOf(SafetyInterlockException.class)
                    .hasMessageContaining("TROLLEY-RUN");
            // Switching back to east is a stop command, and stops are never blocked.
            assertThatCode(() -> guard.check(off(pan))).doesNotThrowAnyException();

            trolley.reportState("OFF", Instant.now());
            pan.reportState("ON", Instant.now());
            assertThatCode(() -> guard.check(on(trolley, Map.of("durationSeconds", 60))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class SignalInterlock {

        private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

        private final DeviceRepository devices = mock(DeviceRepository.class);
        private final SensorRepository sensors = mock(SensorRepository.class);
        private final SensorReadingRepository readings = mock(SensorReadingRepository.class);
        private final SignalInterlockGuard guard = new SignalInterlockGuard(
                REV_A, devices, sensors, readings, Clock.fixed(NOW, ZoneOffset.UTC));
        private final Actuator pump = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");

        private void floatReads(String value, Instant recordedAt) {
            Sensor floatLow = TestFixtures.sensor(5L, tank, "FLOAT-LOW", "tank.float_low_ok", null);
            when(devices.findByDeviceCode("RIO-TANK-01")).thenReturn(Optional.of(tank));
            when(sensors.findByDeviceIdAndMetricKey(1L, "tank.float_low_ok")).thenReturn(Optional.of(floatLow));
            SensorReading reading = new SensorReading();
            reading.setSensor(floatLow);
            reading.setMetricKey("tank.float_low_ok");
            reading.setNumericValue(new BigDecimal(value));
            reading.setRecordedAt(recordedAt);
            when(readings.findFirstBySensorIdOrderByRecordedAtDescIdDesc(5L)).thenReturn(Optional.of(reading));
        }

        @Test
        @DisplayName("a closed float (1.000000 as stored) lets the pumps run")
        void closedFloatPasses() {
            floatReads("1.000000", NOW.minusSeconds(30));

            assertThatCode(() -> guard.check(on(pump, Map.of("durationSeconds", 5)))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an open float, a stale reading or no reading at all blocks the pumps")
        void openStaleOrMissingBlocks() {
            floatReads("0", NOW.minusSeconds(30));
            assertThatThrownBy(() -> guard.check(on(pump, Map.of("durationSeconds", 5))))
                    .isInstanceOf(SafetyInterlockException.class)
                    .hasMessageContaining("FLOAT_LOW");

            floatReads("1", NOW.minus(Duration.ofMinutes(11)));
            assertThatThrownBy(() -> guard.check(on(pump, Map.of("durationSeconds", 5))))
                    .isInstanceOf(SafetyInterlockException.class)
                    .hasMessageContaining("older than");

            when(readings.findFirstBySensorIdOrderByRecordedAtDescIdDesc(5L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> guard.check(on(pump, Map.of("durationSeconds", 5))))
                    .isInstanceOf(SafetyInterlockException.class)
                    .hasMessageContaining("no reading");
        }

        @Test
        @DisplayName("unlisted types and stop commands never look at the float")
        void otherTypesAndStopsPass() {
            Actuator trolley = actuator(20L, panel, "TROLLEY-RUN", "TROLLEY_MOTOR");

            assertThatCode(() -> guard.check(on(trolley, Map.of("durationSeconds", 60)))).doesNotThrowAnyException();
            assertThatCode(() -> guard.check(off(pump))).doesNotThrowAnyException();
            verify(devices, never()).findByDeviceCode(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    class Vocabulary {

        /** Mirrors virtual actuators of hardware/fertimata-rev-a.yml (BE-1); DOSING_LOOP (out of the catalog since V9)
         * stays to cover NUMBER and TEXT parameter kinds. */
        private static final CommandVocabularyProperties VOCABULARY = new CommandVocabularyProperties(List.of(
                new CommandVocabularyProperties.Vocabulary(List.of("DOSING_PUMP"), List.of("ON", "OFF"), null),
                new CommandVocabularyProperties.Vocabulary(
                        List.of("TROLLEY_POSITION"), List.of("HOME", "GOTO_STATION", "STOP"), List.of(
                                new CommandVocabularyProperties.ParameterRule("GOTO_STATION", "station",
                                        CommandVocabularyProperties.Kind.INTEGER, null, BigDecimal.ONE,
                                        BigDecimal.TEN, null))),
                new CommandVocabularyProperties.Vocabulary(
                        List.of("DOSING_LOOP"), List.of("DOSE_TO_TARGET", "STOP"), List.of(
                                new CommandVocabularyProperties.ParameterRule("DOSE_TO_TARGET", "targetEc",
                                        CommandVocabularyProperties.Kind.NUMBER, null, new BigDecimal("0.5"),
                                        new BigDecimal("4.0"), null),
                                new CommandVocabularyProperties.ParameterRule("dose_to_target", "phase",
                                        CommandVocabularyProperties.Kind.TEXT, null, null, null,
                                        List.of("vegetatif", "buah_muda"))))));

        private final CommandVocabularyGuard guard = new CommandVocabularyGuard(VOCABULARY);
        private final Actuator goTo = actuator(50L, panel, "TROLLEY-GOTO", "TROLLEY_POSITION");
        private final Actuator loop = actuator(60L, tank, "DOSING-LOOP", "DOSING_LOOP");

        private static CommandContext command(Actuator actuator, String name, Map<String, Object> parameters) {
            return new CommandContext(actuator, name, parameters, false);
        }

        @Test
        @DisplayName("a command outside the type's vocabulary is refused, case-insensitively matched otherwise")
        void unknownCommandRefused() {
            assertThatThrownBy(() -> guard.check(command(goTo, "START", Map.of())))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("HOME, GOTO_STATION, STOP");
            assertThatCode(() -> guard.check(command(goTo, "home", Map.of()))).doesNotThrowAnyException();
            assertThatCode(() -> guard.check(command(loop, "STOP", Map.of()))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unrestricted type accepts any command, as before the vocabulary existed")
        void unlistedTypeIsOpen() {
            Actuator custom = actuator(70L, panel, "LED", "ILLUMINATION");

            assertThatCode(() -> guard.check(command(custom, "BLINK", Map.of()))).doesNotThrowAnyException();
            assertThat(VOCABULARY.commandsFor("ILLUMINATION")).isNull();
        }

        @Test
        @DisplayName("GOTO_STATION needs a whole station number within the rail")
        void stationParameter() {
            assertThatCode(() -> guard.check(command(goTo, "GOTO_STATION", Map.of("station", 3))))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> guard.check(command(goTo, "GOTO_STATION", Map.of())))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("requires parameter station");
            assertThatThrownBy(() -> guard.check(command(goTo, "GOTO_STATION", Map.of("station", 11))))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("outside 1..10");
            assertThatThrownBy(() -> guard.check(command(goTo, "GOTO_STATION", Map.of("station", 2.5))))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("whole number");
            // A string is refused even when it looks like a number: the edge reads the JSON type.
            assertThatThrownBy(() -> guard.check(command(goTo, "GOTO_STATION", Map.of("station", "3"))))
                    .isInstanceOf(InvalidCommandException.class);
        }

        @Test
        @DisplayName("DOSE_TO_TARGET needs a plausible EC target and a phase the edge knows, spelled exactly")
        void doseParameters() {
            assertThatCode(() -> guard.check(command(loop, "DOSE_TO_TARGET",
                    Map.of("targetEc", 2.2, "phase", "vegetatif")))).doesNotThrowAnyException();
            assertThatThrownBy(() -> guard.check(command(loop, "DOSE_TO_TARGET",
                    Map.of("targetEc", 22, "phase", "vegetatif"))))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("targetEc");
            assertThatThrownBy(() -> guard.check(command(loop, "DOSE_TO_TARGET",
                    Map.of("targetEc", 2.2, "phase", "Vegetatif"))))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("vegetatif, buah_muda");
            assertThatThrownBy(() -> guard.check(command(loop, "DOSE_TO_TARGET", Map.of("targetEc", 2.2))))
                    .isInstanceOf(InvalidCommandException.class)
                    .hasMessageContaining("requires parameter phase");
            // Rules belong to one command: STOP carries no parameters at all.
            assertThatCode(() -> guard.check(command(loop, "STOP", Map.of()))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("STOP of a virtual actuator is never held by an interlock, START is")
        void virtualStopNeverBlocked() {
            ActuatorSafetyProperties virtual = new ActuatorSafetyProperties(
                    List.of("ON", "START", "HOME", "GOTO_STATION"), null, null,
                    List.of("TROLLEY_PATROL"), List.of(List.of("DOSING_PUMP", "TROLLEY_PATROL")), null, false, null,
                    null, null);
            DeviceService deviceService = mock(DeviceService.class);
            Device jetson = TestFixtures.onlineDevice(9L, "JETSON-01");
            jetson.reportPowerSource("BATTERY", Instant.now());
            when(deviceService.devicesOnBackupPower()).thenReturn(List.of(jetson));
            ActuatorRepository repository = mock(ActuatorRepository.class);
            Actuator patrol = actuator(80L, jetson, "TROLLEY-PATROL", "TROLLEY_PATROL");
            Actuator pump = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");
            pump.reportState("ON", Instant.now());
            when(repository.findAllWithDevice()).thenReturn(List.of(patrol, pump));
            CommandSafetyPolicy policy = new CommandSafetyPolicy(virtual, List.of(
                    new BackupPowerGuard(virtual, deviceService), new ExclusiveOperationGuard(virtual, repository)));

            assertThatThrownBy(() -> policy.check(patrol, "START", Map.of()))
                    .isInstanceOf(SafetyInterlockException.class);
            assertThatCode(() -> policy.check(patrol, "STOP", Map.of())).doesNotThrowAnyException();

            when(deviceService.devicesOnBackupPower()).thenReturn(List.of());
            assertThatThrownBy(() -> policy.check(patrol, "START", Map.of()))
                    .isInstanceOf(SafetyInterlockException.class)
                    .hasMessageContaining("DOSING-N");
        }
    }

    @Nested
    class DeviceOnline {

        private final DeviceOnlineGuard guard = new DeviceOnlineGuard(REV_A);

        @Test
        @DisplayName("an activating command for a device that is not ONLINE is refused, a stop is not")
        void offlineDeviceRefusesActivation() {
            Device quiet = TestFixtures.device(3L, "RIO-TANK-01");
            quiet.setStatus(DeviceStatus.OFFLINE);
            Actuator pump = actuator(10L, quiet, "DOSING-N", "DOSING_PUMP");

            assertThatThrownBy(() -> guard.check(on(pump, Map.of("durationSeconds", 5))))
                    .isInstanceOf(SafetyInterlockException.class);
            assertThatCode(() -> guard.check(off(pump))).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("inert defaults keep phase-1 behaviour: nothing is required or refused")
    void inertDefaults() {
        ActuatorSafetyProperties inert = ActuatorSafetyProperties.inert();
        CommandSafetyPolicy policy = new CommandSafetyPolicy(
                inert,
                List.of(new RunDurationGuard(inert), new DeviceOnlineGuard(inert)));
        Device offline = TestFixtures.device(4L, "JETSON-001");
        Actuator output = TestFixtures.actuator(40L, offline, "ACTUATOR-001");

        assertThatCode(() -> policy.check(output, "ON", Map.of())).doesNotThrowAnyException();
        OutboundCommand command = new OutboundCommand("uid", "JETSON-001", "ACTUATOR-001", "ON", Map.of(),
                Instant.now(), null);
        assertThat(policy.withExpiry(command).expiresAt()).isNull();
    }

    @Test
    @DisplayName("a configured TTL stamps expiresAt relative to the issue time")
    void ttlStampsExpiry() {
        CommandSafetyPolicy policy = new CommandSafetyPolicy(REV_A, List.of());
        Instant issued = Instant.parse("2026-09-17T10:00:00Z");
        OutboundCommand command =
                new OutboundCommand("uid", "PANEL-01", "DIST-PUMP", "ON", Map.of(), issued, null);

        assertThat(policy.withExpiry(command).expiresAt()).isEqualTo(Instant.parse("2026-09-17T10:00:30Z"));
    }
}
