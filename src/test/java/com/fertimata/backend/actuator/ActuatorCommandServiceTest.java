package com.fertimata.backend.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorCommand;
import com.fertimata.backend.actuator.domain.ActuatorCommandRepository;
import com.fertimata.backend.actuator.domain.ActuatorCommandTransport;
import com.fertimata.backend.actuator.domain.CommandSource;
import com.fertimata.backend.actuator.domain.CommandStatus;
import com.fertimata.backend.actuator.domain.OutboundCommand;
import com.fertimata.backend.actuator.dto.ActuatorCommandRequest;
import com.fertimata.backend.actuator.dto.ActuatorCommandResponse;
import com.fertimata.backend.actuator.safety.CommandSafetyPolicy;
import com.fertimata.backend.actuator.safety.SafetyInterlockException;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.common.exception.InvalidCommandException;
import com.fertimata.backend.common.exception.MessagePublishException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.mqtt.payload.CommandAckPayload;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Instant;
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
class ActuatorCommandServiceTest {

    @Mock
    private ActuatorService actuatorService;

    @Mock
    private ActuatorCommandRecorder recorder;

    @Mock
    private ActuatorCommandRepository commandRepository;

    @Mock
    private ActuatorCommandTransport transport;

    @Mock
    private CommandSafetyPolicy safetyPolicy;

    @Mock
    private AlertService alertService;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Mock
    private JsonSupport jsonSupport;

    private ActuatorCommandService commandService;
    private Device device;
    private Actuator actuator;

    @BeforeEach
    void setUp() {
        commandService = new ActuatorCommandService(
                actuatorService,
                recorder,
                commandRepository,
                transport,
                safetyPolicy,
                alertService,
                realtimeEventPublisher,
                jsonSupport);

        device = TestFixtures.onlineDevice(1L, "JETSON-001");
        actuator = TestFixtures.actuator(5L, device, "ACTUATOR-001");
        when(actuatorService.require(5L)).thenReturn(actuator);
        when(safetyPolicy.withExpiry(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("a valid command is persisted, published and recorded as SENT")
    void validCommandIsPublished() {
        ActuatorCommandRecorder.Recorded recorded = recorded("cmd-1");
        when(recorder.createPending(eq(5L), eq("ON"), any(), eq(CommandSource.OPERATOR), eq("operator")))
                .thenReturn(recorded);
        when(recorder.markSent(eq(42L), any())).thenReturn(response(CommandStatus.SENT));

        ActuatorCommandResponse response = commandService.issueOperatorCommand(
                5L, new ActuatorCommandRequest("ON", Map.of("durationSeconds", 60)), "operator");

        assertThat(response.status()).isEqualTo(CommandStatus.SENT);
        ArgumentCaptor<OutboundCommand> captor = ArgumentCaptor.forClass(OutboundCommand.class);
        verify(transport).send(captor.capture());
        assertThat(captor.getValue().actuatorCode()).isEqualTo("ACTUATOR-001");
        assertThat(captor.getValue().deviceCode()).isEqualTo("JETSON-001");
        assertThat(captor.getValue().parameters()).containsEntry("durationSeconds", 60);
        verify(realtimeEventPublisher).publish(eq(RealtimeEventType.ACTUATOR_COMMAND_UPDATED), any());
    }

    @Test
    @DisplayName("the configured wire expiry is what reaches the transport")
    void expiryIsStampedBeforeSending() {
        ActuatorCommandRecorder.Recorded recorded = recorded("cmd-ttl");
        when(recorder.createPending(anyLong(), anyString(), any(), any(), anyString())).thenReturn(recorded);
        when(recorder.markSent(eq(42L), any())).thenReturn(response(CommandStatus.SENT));
        Instant expiry = Instant.parse("2026-09-17T10:00:30Z");
        when(safetyPolicy.withExpiry(any())).thenAnswer(invocation -> {
            OutboundCommand outbound = invocation.getArgument(0);
            return outbound.expiringAt(expiry);
        });

        commandService.issueOperatorCommand(5L, new ActuatorCommandRequest("ON", null), "operator");

        ArgumentCaptor<OutboundCommand> captor = ArgumentCaptor.forClass(OutboundCommand.class);
        verify(transport).send(captor.capture());
        assertThat(captor.getValue().expiresAt()).isEqualTo(expiry);
    }

    @Test
    @DisplayName("an interlock refusal records nothing and publishes nothing")
    void interlockRefusalHasNoSideEffects() {
        org.mockito.Mockito.doThrow(new SafetyInterlockException("on battery", Map.of()))
                .when(safetyPolicy)
                .check(any(), anyString(), any());

        assertThatThrownBy(() ->
                        commandService.issueOperatorCommand(5L, new ActuatorCommandRequest("ON", null), "operator"))
                .isInstanceOf(SafetyInterlockException.class);

        verify(recorder, never()).createPending(anyLong(), anyString(), any(), any(), anyString());
        verify(transport, never()).send(any());
    }

    @Test
    @DisplayName("a disabled actuator refuses commands and nothing is published or recorded")
    void disabledActuatorRejectsCommand() {
        actuator.setEnabled(false);

        assertThatThrownBy(() ->
                        commandService.issueOperatorCommand(5L, new ActuatorCommandRequest("ON", null), "operator"))
                .isInstanceOf(InvalidCommandException.class)
                .hasMessageContaining("disabled");

        verify(transport, never()).send(any());
        verify(recorder, never()).createPending(anyLong(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("a publish failure still leaves a FAILED audit record, an alert and a 503-mapped error")
    void publishFailureIsRecordedAndAlerted() {
        ActuatorCommandRecorder.Recorded recorded = recorded("cmd-2");
        when(recorder.createPending(anyLong(), anyString(), any(), any(), anyString())).thenReturn(recorded);
        when(recorder.markFailed(eq(42L), anyString(), any())).thenReturn(response(CommandStatus.FAILED));
        org.mockito.Mockito.doThrow(new MessagePublishException("broker unreachable"))
                .when(transport)
                .send(any());

        assertThatThrownBy(() ->
                        commandService.issueOperatorCommand(5L, new ActuatorCommandRequest("ON", null), "operator"))
                .isInstanceOf(MessagePublishException.class);

        verify(recorder).markFailed(eq(42L), anyString(), any());
        verify(recorder, never()).markSent(anyLong(), any());

        ArgumentCaptor<NewAlert> alert = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raise(alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(AlertType.ACTUATOR_COMMAND_FAILED);
    }

    @Test
    @DisplayName("only a device acknowledgement moves a command to EXECUTED")
    void acknowledgementMarksExecuted() {
        ActuatorCommand command = pendingCommand(CommandStatus.SENT);
        when(commandRepository.findByCommandUid("cmd-3")).thenReturn(Optional.of(command));

        Instant executedAt = Instant.parse("2026-09-15T05:00:00Z");
        commandService.applyAcknowledgement(
                "JETSON-001", new CommandAckPayload("cmd-3", true, "ON", executedAt, null));

        assertThat(command.getStatus()).isEqualTo(CommandStatus.EXECUTED);
        assertThat(command.getExecutedAt()).isEqualTo(executedAt);
        verify(actuatorService).reportState(eq(device), eq("ACTUATOR-001"), eq("ON"), eq(executedAt));
    }

    @Test
    @DisplayName("a device-reported failure marks the command FAILED and raises an alert")
    void failedAcknowledgementRaisesAlert() {
        ActuatorCommand command = pendingCommand(CommandStatus.SENT);
        when(commandRepository.findByCommandUid("cmd-4")).thenReturn(Optional.of(command));

        commandService.applyAcknowledgement(
                "JETSON-001", new CommandAckPayload("cmd-4", false, null, Instant.now(), "relay stuck"));

        assertThat(command.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(command.getErrorMessage()).isEqualTo("relay stuck");
        verify(alertService).raise(any());
    }

    @Test
    @DisplayName("an acknowledgement for an unknown command id is ignored, not guessed at")
    void unknownAcknowledgementIsIgnored() {
        when(commandRepository.findByCommandUid("nope")).thenReturn(Optional.empty());

        commandService.applyAcknowledgement("JETSON-001", new CommandAckPayload("nope", true, null, null, null));

        verify(realtimeEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("a repeated acknowledgement does not rewrite a command that already finished")
    void repeatedAcknowledgementIsIgnored() {
        ActuatorCommand command = pendingCommand(CommandStatus.EXECUTED);
        when(commandRepository.findByCommandUid("cmd-5")).thenReturn(Optional.of(command));

        commandService.applyAcknowledgement(
                "JETSON-001", new CommandAckPayload("cmd-5", false, null, Instant.now(), "late failure"));

        assertThat(command.getStatus()).isEqualTo(CommandStatus.EXECUTED);
        assertThat(command.getErrorMessage()).isNull();
    }

    private ActuatorCommandRecorder.Recorded recorded(String uid) {
        OutboundCommand outbound = new OutboundCommand(
                uid, "JETSON-001", "ACTUATOR-001", "ON", Map.of("durationSeconds", 60), Instant.now(), null);
        return new ActuatorCommandRecorder.Recorded(42L, outbound, response(CommandStatus.PENDING));
    }

    private ActuatorCommandResponse response(CommandStatus status) {
        return new ActuatorCommandResponse(
                42L,
                "cmd-1",
                5L,
                "ACTUATOR-001",
                1L,
                "JETSON-001",
                "ON",
                Map.of(),
                CommandSource.OPERATOR,
                "operator",
                status,
                Instant.now(),
                null,
                null,
                null);
    }

    private ActuatorCommand pendingCommand(CommandStatus status) {
        ActuatorCommand command = new ActuatorCommand();
        TestFixtures.setId(command, 42L);
        command.setActuator(actuator);
        command.setDevice(device);
        command.setCommandType("ON");
        command.setSource(CommandSource.OPERATOR);
        command.setStatus(status);
        command.setRequestedAt(Instant.now());
        return command;
    }
}
