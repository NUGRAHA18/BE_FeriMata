package com.fertimata.backend.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.ActuatorCommand;
import com.fertimata.backend.actuator.domain.ActuatorCommandRepository;
import com.fertimata.backend.actuator.domain.CommandSource;
import com.fertimata.backend.actuator.domain.CommandStatus;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class CommandResponseTimeoutJobTest {

    private static final Instant NOW = Instant.parse("2026-10-29T07:00:00Z");

    @Mock
    private ActuatorCommandRepository repository;

    @Mock
    private ActuatorCommandRecorder recorder;

    @Mock
    private AlertService alertService;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final Device device = TestFixtures.device(1L, "JETSON-01");

    private CommandResponseTimeoutJob job() {
        return new CommandResponseTimeoutJob(
                repository,
                recorder,
                alertService,
                realtimeEventPublisher,
                new CommandResponseProperties(Duration.ofMinutes(2), Duration.ofSeconds(30), Duration.ofHours(1)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
    }

    private ActuatorCommand command(String code, String type, Instant requestedAt) {
        ActuatorCommand command = new ActuatorCommand();
        command.setActuator(TestFixtures.actuator(5L, device, code));
        command.setDevice(device);
        command.setCommandType(type);
        command.setCommandUid(code + "-" + type);
        command.setSource(CommandSource.OPERATOR);
        command.setStatus(CommandStatus.SENT);
        command.setRequestedAt(requestedAt);
        return command;
    }

    @Test
    @DisplayName("commands with no answer after the timeout fail as NO_RESPONSE; only recent ones raise an alert")
    void closesUnansweredCommands() {
        ActuatorCommand recent = command("TROLLEY-PATROL", "START", NOW.minus(Duration.ofMinutes(3)));
        // Sisa uji 26 Okt: ditutup diam-diam, tanpa membanjiri peringatan.
        ActuatorCommand leftover = command("TROLLEY-GOTO", "GOTO_STATION", NOW.minus(Duration.ofDays(2)));
        when(repository.findByStatusInAndRequestedAtBefore(any(), eq(NOW.minus(Duration.ofMinutes(2)))))
                .thenReturn(List.of(recent, leftover));

        int closed = job().closeUnanswered(NOW);

        assertThat(closed).isEqualTo(2);
        assertThat(List.of(recent, leftover)).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(CommandStatus.FAILED);
            assertThat(c.getFailureCode()).isEqualTo(ActuatorCommand.NO_RESPONSE);
            assertThat(c.isAwaitingLateAck()).isTrue();
            assertThat(c.getErrorMessage()).contains("tidak menjawab dalam 2 menit");
        });
        verify(realtimeEventPublisher, times(2)).publish(eq(RealtimeEventType.ACTUATOR_COMMAND_UPDATED), any());
        verify(alertService, times(1)).raise(any());
    }
}
