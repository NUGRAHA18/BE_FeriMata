package com.fertimata.backend.actuator;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorCommand;
import com.fertimata.backend.actuator.domain.ActuatorCommandRepository;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.actuator.domain.CommandSource;
import com.fertimata.backend.actuator.domain.OutboundCommand;
import com.fertimata.backend.actuator.dto.ActuatorCommandResponse;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.user.User;
import com.fertimata.backend.user.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the lifecycle of an actuator command, each step in its own transaction.
 *
 * <p>This split exists for one reason: the audit record must survive a failed publish. If creating
 * the record and publishing shared a transaction, a broker outage would roll back the very row that
 * documents the attempt, and the operator would see nothing where they should see a failed command.
 *
 * <p>Every method returns a detached view rather than a managed entity, so callers cannot trip over
 * a lazy association after the transaction has closed.
 */
@Component
public class ActuatorCommandRecorder {

    private final ActuatorCommandRepository commandRepository;
    private final ActuatorRepository actuatorRepository;
    private final UserRepository userRepository;
    private final JsonSupport jsonSupport;

    public ActuatorCommandRecorder(
            ActuatorCommandRepository commandRepository,
            ActuatorRepository actuatorRepository,
            UserRepository userRepository,
            JsonSupport jsonSupport) {
        this.commandRepository = commandRepository;
        this.actuatorRepository = actuatorRepository;
        this.userRepository = userRepository;
        this.jsonSupport = jsonSupport;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recorded createPending(
            Long actuatorId,
            String commandType,
            Map<String, Object> parameters,
            CommandSource source,
            String username) {

        Actuator actuator = actuatorRepository
                .findById(actuatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Actuator", actuatorId));

        ActuatorCommand command = new ActuatorCommand();
        command.setActuator(actuator);
        command.setDevice(actuator.getDevice());
        command.setCommandType(commandType);
        command.setParameters(jsonSupport.toJson(parameters));
        command.setSource(source);
        command.setRequestedBy(resolveRequester(source, username));
        command.setRequestedAt(Instant.now());

        ActuatorCommand saved = commandRepository.save(command);
        return toRecorded(saved, parameters);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActuatorCommandResponse markSent(Long commandId, Instant at) {
        ActuatorCommand command = require(commandId);
        command.markSent(at);
        return toResponse(command);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActuatorCommandResponse markFailed(Long commandId, String reason, Instant at) {
        ActuatorCommand command = require(commandId);
        command.markFailed(reason, at);
        return toResponse(command);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActuatorCommandResponse markExecuted(Long commandId, Instant at) {
        ActuatorCommand command = require(commandId);
        command.markExecuted(at);
        return toResponse(command);
    }

    @Transactional(readOnly = true)
    public Optional<ActuatorCommand> findByUid(String commandUid) {
        return commandRepository.findByCommandUid(commandUid);
    }

    public ActuatorCommandResponse toResponse(ActuatorCommand command) {
        return ActuatorCommandResponse.from(command, jsonSupport.toMap(command.getParameters()));
    }

    private Recorded toRecorded(ActuatorCommand command, Map<String, Object> parameters) {
        OutboundCommand outbound = new OutboundCommand(
                command.getCommandUid(),
                command.getDevice().getDeviceCode(),
                command.getActuator().getCode(),
                command.getCommandType(),
                parameters == null ? Map.of() : parameters,
                command.getRequestedAt(),
                null);
        return new Recorded(command.getId(), outbound, toResponse(command));
    }

    private ActuatorCommand require(Long commandId) {
        return commandRepository
                .findById(commandId)
                .orElseThrow(() -> new ResourceNotFoundException("ActuatorCommand", commandId));
    }

    private User resolveRequester(CommandSource source, String username) {
        if (source != CommandSource.OPERATOR || username == null) {
            return null;
        }
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    /** A freshly persisted command: its id, what to put on the wire, and what to return to the API. */
    public record Recorded(Long id, OutboundCommand outbound, ActuatorCommandResponse response) {}
}
