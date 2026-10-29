package com.fertimata.backend.irrigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.ActuatorCommandService;
import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.dto.ActuatorCommandRequest;
import com.fertimata.backend.actuator.dto.ActuatorCommandResponse;
import com.fertimata.backend.actuator.safety.SafetyInterlockException;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.support.TestFixtures;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IrrigationScheduleRunnerTest {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    /** 06:02 WIB. */
    private static final Instant NOW = Instant.parse("2026-10-29T23:02:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @Mock
    private IrrigationScheduleService schedules;

    @Mock
    private ActuatorCommandService commandService;

    @Mock
    private AlertService alertService;

    @Mock
    private ActuatorCommandResponse commandResponse;

    private IrrigationScheduleRunner runner;
    private Actuator pump;

    @BeforeEach
    void setUp() {
        runner = new IrrigationScheduleRunner(
                schedules,
                commandService,
                alertService,
                new IrrigationProperties(true, "DIST-PUMP", Duration.ofSeconds(30), Duration.ofMinutes(15)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        pump = TestFixtures.actuator(9L, TestFixtures.device(1L, "PANEL-01"), "DIST-PUMP");
        when(schedules.zone()).thenReturn(WIB);
    }

    @Test
    @DisplayName("a due schedule claims its day first, then starts the pump for its duration")
    void startsDueSchedule() {
        when(schedules.due(TODAY, LocalTime.of(6, 2))).thenReturn(List.of(new IrrigationScheduleService.Due(1L, LocalTime.of(6, 0), 300)));
        when(schedules.claim(1L, TODAY, NOW)).thenReturn(true);
        when(schedules.pump()).thenReturn(Optional.of(pump));
        when(commandResponse.commandUid()).thenReturn("cmd-1");
        when(commandService.issueSystemCommand(eq(9L), any())).thenReturn(commandResponse);

        runner.runDue(NOW);

        ArgumentCaptor<ActuatorCommandRequest> request = ArgumentCaptor.forClass(ActuatorCommandRequest.class);
        verify(commandService).issueSystemCommand(eq(9L), request.capture());
        assertThat(request.getValue().command()).isEqualTo("ON");
        assertThat(request.getValue().parameters()).isEqualTo(Map.of("durationSeconds", 300));
        verify(schedules).record(1L, TODAY, NOW, IrrigationSchedule.RunResult.STARTED, null, null, "cmd-1");
        verify(alertService, never()).raise(any());
    }

    @Test
    @DisplayName("a schedule missed while the backend was down is not watered late")
    void missedScheduleIsNotChased() {
        when(schedules.due(TODAY, LocalTime.of(6, 2))).thenReturn(List.of(new IrrigationScheduleService.Due(2L, LocalTime.of(5, 0), 300)));
        when(schedules.claim(2L, TODAY, NOW)).thenReturn(true);

        runner.runDue(NOW);

        verify(commandService, never()).issueSystemCommand(any(), any());
        verify(schedules).record(eq(2L), eq(TODAY), eq(NOW), eq(IrrigationSchedule.RunResult.MISSED), eq("BACKEND_DOWN"), anyString(), isNull());
    }

    @Test
    @DisplayName("a watering refused by a safety rule is recorded as skipped and raises a warning")
    void refusedBySafetyRule() {
        when(schedules.due(TODAY, LocalTime.of(6, 2))).thenReturn(List.of(new IrrigationScheduleService.Due(1L, LocalTime.of(6, 0), 300)));
        when(schedules.claim(1L, TODAY, NOW)).thenReturn(true);
        when(schedules.pump()).thenReturn(Optional.of(pump));
        when(commandService.issueSystemCommand(eq(9L), any()))
                .thenThrow(new SafetyInterlockException("Running on backup power", Map.of("interlock", "BACKUP_POWER")));

        runner.runDue(NOW);

        verify(schedules).record(1L, TODAY, NOW, IrrigationSchedule.RunResult.SKIPPED, "BACKUP_POWER", "Running on backup power", null);
        ArgumentCaptor<NewAlert> alert = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raise(alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(AlertType.IRRIGATION_SKIPPED);
        assertThat(alert.getValue().metadata())
                .containsEntry("time", "06:00")
                .containsEntry("interlock", "BACKUP_POWER")
                .containsEntry("reasonCode", "BACKUP_POWER");
    }

    @Test
    @DisplayName("without an enabled pump the run fails with a warning instead of throwing")
    void missingPump() {
        when(schedules.due(TODAY, LocalTime.of(6, 2))).thenReturn(List.of(new IrrigationScheduleService.Due(1L, LocalTime.of(6, 0), 300)));
        when(schedules.claim(1L, TODAY, NOW)).thenReturn(true);
        when(schedules.pump()).thenReturn(Optional.empty());

        runner.runDue(NOW);

        verify(schedules).record(eq(1L), eq(TODAY), eq(NOW), eq(IrrigationSchedule.RunResult.FAILED), eq("PUMP_UNAVAILABLE"), anyString(), isNull());
        verify(alertService).raise(any());
    }

    @Test
    @DisplayName("a schedule another check already took is not run twice")
    void claimedScheduleIsSkipped() {
        when(schedules.due(TODAY, LocalTime.of(6, 2))).thenReturn(List.of(new IrrigationScheduleService.Due(1L, LocalTime.of(6, 0), 300)));
        when(schedules.claim(1L, TODAY, NOW)).thenReturn(false);

        runner.runDue(NOW);

        verify(commandService, never()).issueSystemCommand(any(), any());
    }
}
