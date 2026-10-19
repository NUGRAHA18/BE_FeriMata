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
import com.fertimata.backend.device.DeviceService;
import com.fertimata.backend.device.DeviceStatus;
import com.fertimata.backend.support.TestFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The interlocks are the part of the backend that stands between a click and a pump, so each rule is
 * pinned down here - including the one that must hold for every guard: a stop is never blocked.
 */
class CommandGuardsTest {

    /** Mirrors the FERTIMATA Rev A policy in hardware/fertimata-rev-a.yml. */
    private static final ActuatorSafetyProperties REV_A = new ActuatorSafetyProperties(
            List.of("ON"),
            "durationSeconds",
            List.of("DOSING_PUMP", "TROLLEY_MOTOR"),
            List.of("DOSING_PUMP", "TROLLEY_MOTOR"),
            List.of(List.of("DOSING_PUMP", "TROLLEY_MOTOR"), List.of("DOSING_PUMP", "ILLUMINATION")),
            List.of("ON"),
            true,
            Duration.ofSeconds(30));

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
            Actuator led = actuator(30L, panel, "LED-ILLUMINATION", "ILLUMINATION");

            assertThatCode(() -> guard.check(on(led, Map.of()))).doesNotThrowAnyException();
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
            Actuator led = actuator(30L, panel, "LED-ILLUMINATION", "ILLUMINATION");
            Actuator pump = actuator(10L, tank, "DOSING-N", "DOSING_PUMP");

            assertThatCode(() -> guard.check(on(led, Map.of()))).doesNotThrowAnyException();
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
            Actuator led = actuator(30L, panel, "LED-ILLUMINATION", "ILLUMINATION");
            when(repository.findAllWithDevice()).thenReturn(List.of(n, p, trolley, led));

            // Trolley and LED share no group: moving with the light on is allowed.
            led.reportState("ON", Instant.now());
            assertThatCode(() -> guard.check(on(trolley, Map.of("durationSeconds", 60))))
                    .doesNotThrowAnyException();

            // Two dosing pumps are the same type: sequencing them is the edge agent's call.
            led.reportState("OFF", Instant.now());
            p.reportState("ON", Instant.now());
            assertThatCode(() -> guard.check(on(n, Map.of("durationSeconds", 5)))).doesNotThrowAnyException();

            // The group is symmetric: a running dosing pump also holds the trolley.
            assertThatThrownBy(() -> guard.check(on(trolley, Map.of("durationSeconds", 60))))
                    .isInstanceOf(SafetyInterlockException.class);
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
