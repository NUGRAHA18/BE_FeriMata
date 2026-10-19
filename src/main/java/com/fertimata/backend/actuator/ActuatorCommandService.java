package com.fertimata.backend.actuator;

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
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.alert.AlertSource;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.common.exception.InvalidCommandException;
import com.fertimata.backend.common.exception.MessagePublishException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.DeviceStatus;
import com.fertimata.backend.mqtt.payload.CommandAckPayload;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts, dispatches and tracks actuator commands.
 *
 * <p>The flow is deliberately explicit, because this is the one place where the backend causes
 * something physical to happen:
 *
 * <ol>
 *   <li>authorise and validate the request, including the configured interlocks
 *   <li>persist a PENDING audit record, in its own transaction
 *   <li>hand it to the transport
 *   <li>record SENT, or FAILED plus an alert
 * </ol>
 *
 * <p>The service never touches MQTT: it depends on {@link ActuatorCommandTransport}, a port the
 * domain owns. And it never claims a command was executed - only a device acknowledgement does that.
 */
@Service
public class ActuatorCommandService {

    private static final Logger log = LoggerFactory.getLogger(ActuatorCommandService.class);

    private final ActuatorService actuatorService;
    private final ActuatorCommandRecorder recorder;
    private final ActuatorCommandRepository commandRepository;
    private final ActuatorCommandTransport transport;
    private final CommandSafetyPolicy safetyPolicy;
    private final AlertService alertService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final JsonSupport jsonSupport;

    public ActuatorCommandService(
            ActuatorService actuatorService,
            ActuatorCommandRecorder recorder,
            ActuatorCommandRepository commandRepository,
            ActuatorCommandTransport transport,
            CommandSafetyPolicy safetyPolicy,
            AlertService alertService,
            RealtimeEventPublisher realtimeEventPublisher,
            JsonSupport jsonSupport) {
        this.actuatorService = actuatorService;
        this.recorder = recorder;
        this.commandRepository = commandRepository;
        this.transport = transport;
        this.safetyPolicy = safetyPolicy;
        this.alertService = alertService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.jsonSupport = jsonSupport;
    }

    /**
     * Issues a command on behalf of a signed-in operator.
     *
     * @param username the authenticated account, taken from the security context by the controller -
     *     never from the request body
     */
    public ActuatorCommandResponse issueOperatorCommand(
            Long actuatorId, ActuatorCommandRequest request, String username) {
        return issue(actuatorId, request, CommandSource.OPERATOR, username);
    }

    /** Issues a command the backend originated itself. */
    public ActuatorCommandResponse issueSystemCommand(Long actuatorId, ActuatorCommandRequest request) {
        return issue(actuatorId, request, CommandSource.SYSTEM, null);
    }

    private ActuatorCommandResponse issue(
            Long actuatorId, ActuatorCommandRequest request, CommandSource source, String username) {

        Actuator actuator = actuatorService.require(actuatorId);
        Map<String, Object> parameters = request.parameters() == null ? Map.of() : request.parameters();
        validate(actuator, request, parameters);

        ActuatorCommandRecorder.Recorded recorded =
                recorder.createPending(actuatorId, request.command(), parameters, source, username);
        OutboundCommand outbound = safetyPolicy.withExpiry(recorded.outbound());

        log.info(
                "Command {} requested for actuator {} on device {} by {} ({})",
                request.command(),
                recorded.outbound().actuatorCode(),
                recorded.outbound().deviceCode(),
                username == null ? source.name() : username,
                recorded.outbound().commandUid());

        try {
            transport.send(outbound);
        } catch (MessagePublishException ex) {
            ActuatorCommandResponse failed =
                    recorder.markFailed(recorded.id(), ex.getMessage(), Instant.now());
            log.error(
                    "Command {} could not be delivered for actuator {}: {}",
                    recorded.outbound().commandUid(),
                    recorded.outbound().actuatorCode(),
                    ex.getMessage());
            alertService.raise(NewAlert.builder(AlertType.ACTUATOR_COMMAND_FAILED, AlertSeverity.WARNING)
                    .title("Actuator command not delivered")
                    .message("Command %s for actuator %s could not be delivered: %s"
                            .formatted(request.command(), recorded.outbound().actuatorCode(), ex.getMessage()))
                    .source(AlertSource.SYSTEM)
                    .actuator(actuator)
                    .device(actuator.getDevice())
                    .metadata(Map.of("commandUid", recorded.outbound().commandUid()))
                    .build());
            realtimeEventPublisher.publish(RealtimeEventType.ACTUATOR_COMMAND_UPDATED, failed);
            throw ex;
        }

        ActuatorCommandResponse sent = recorder.markSent(recorded.id(), Instant.now());
        realtimeEventPublisher.publish(RealtimeEventType.ACTUATOR_COMMAND_UPDATED, sent);
        return sent;
    }

    private void validate(Actuator actuator, ActuatorCommandRequest request, Map<String, Object> parameters) {
        if (!actuator.isEnabled()) {
            throw new InvalidCommandException(
                    "Actuator %s is disabled and cannot accept commands".formatted(actuator.getCode()),
                    Map.of("actuatorCode", actuator.getCode()));
        }
        // The command vocabulary belongs to the device, so the name is not checked against a list
        // here. Run-time limits, power and exclusivity interlocks are configuration-driven guards.
        safetyPolicy.check(actuator, request.command(), parameters);
        if (actuator.getDevice().getStatus() != DeviceStatus.ONLINE) {
            // Not an error by default: the broker holds the message and the wire expiry bounds how
            // long it stays valid. app.actuator.safety.refuse-when-device-offline turns this into a
            // refusal for activating commands.
            log.warn(
                    "Issuing command {} while device {} is {}",
                    request.command(),
                    actuator.getDevice().getDeviceCode(),
                    actuator.getDevice().getStatus());
        }
    }

    /**
     * Applies a device acknowledgement.
     *
     * <p>This is the only path that can mark a command EXECUTED. An acknowledgement for an unknown
     * correlation id is logged and dropped rather than guessed at.
     */
    @Transactional
    public void applyAcknowledgement(String deviceCode, CommandAckPayload ack) {
        if (ack == null || ack.commandUid() == null || ack.commandUid().isBlank()) {
            log.warn("Ignoring command acknowledgement from {} without a command id", deviceCode);
            return;
        }

        Optional<ActuatorCommand> found = commandRepository.findByCommandUid(ack.commandUid());
        if (found.isEmpty()) {
            log.warn("Received acknowledgement for unknown command {} from {}", ack.commandUid(), deviceCode);
            return;
        }

        ActuatorCommand command = found.get();
        if (command.getStatus().isTerminal()) {
            log.debug("Ignoring repeated acknowledgement for command {}", ack.commandUid());
            return;
        }

        Instant at = ack.executedAt() == null ? Instant.now() : ack.executedAt();
        boolean success = Boolean.TRUE.equals(ack.success());
        if (success) {
            command.markExecuted(at);
            log.info("Command {} executed on device {}", ack.commandUid(), deviceCode);
        } else {
            String reason = ack.errorMessage() == null ? "Device reported failure" : ack.errorMessage();
            command.markFailed(reason, at);
            log.warn("Command {} failed on device {}: {}", ack.commandUid(), deviceCode, reason);
            alertService.raise(NewAlert.builder(AlertType.ACTUATOR_COMMAND_FAILED, AlertSeverity.WARNING)
                    .title("Actuator command failed on the device")
                    .message("Device %s reported that command %s failed: %s"
                            .formatted(deviceCode, command.getCommandType(), reason))
                    .source(AlertSource.DEVICE)
                    .actuator(command.getActuator())
                    .device(command.getDevice())
                    .metadata(Map.of("commandUid", ack.commandUid()))
                    .build());
        }

        if (ack.actuatorState() != null) {
            actuatorService.reportState(
                    command.getDevice(), command.getActuator().getCode(), ack.actuatorState(), at);
        }
        realtimeEventPublisher.publish(RealtimeEventType.ACTUATOR_COMMAND_UPDATED, recorder.toResponse(command));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActuatorCommandResponse> history(Long actuatorId, Pageable pageable) {
        actuatorService.require(actuatorId);
        return PageResponse.from(
                commandRepository.findByActuatorIdOrderByRequestedAtDesc(actuatorId, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActuatorCommandResponse> recent(Pageable pageable) {
        return PageResponse.from(commandRepository.findRecent(pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public ActuatorCommandResponse get(Long commandId) {
        return commandRepository
                .findById(commandId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("ActuatorCommand", commandId));
    }

    @Transactional(readOnly = true)
    public long countSince(CommandStatus status, Instant since) {
        return commandRepository.countByStatusAndRequestedAtAfter(status, since);
    }

    /** Name of the transport currently in use, so the dashboard can show mock versus real messaging. */
    public String transportDescription() {
        return transport.describe();
    }

    public boolean transportAvailable() {
        return transport.isAvailable();
    }

    private ActuatorCommandResponse toResponse(ActuatorCommand command) {
        return ActuatorCommandResponse.from(command, jsonSupport.toMap(command.getParameters()));
    }
}
