package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.OutboundCommand;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs every {@link CommandGuard} and stamps the wire expiry on outgoing commands.
 *
 * <p>The command service depends on this one class rather than on the individual guards, so adding
 * an interlock never touches the command flow.
 */
@Component
public class CommandSafetyPolicy {

    private static final Logger log = LoggerFactory.getLogger(CommandSafetyPolicy.class);

    /** What an unquoted YAML ON/OFF becomes after binding. */
    private static final List<String> PARSED_BOOLEANS = List.of("TRUE", "FALSE");

    private final ActuatorSafetyProperties properties;
    private final List<CommandGuard> guards;

    public CommandSafetyPolicy(ActuatorSafetyProperties properties, List<CommandGuard> guards) {
        this.properties = properties;
        this.guards = List.copyOf(guards);
        if (properties.activatingCommands().stream().anyMatch(PARSED_BOOLEANS::contains)
                || properties.activeStates().stream().anyMatch(PARSED_BOOLEANS::contains)) {
            log.warn(
                    "app.actuator.safety lists contain TRUE/FALSE ({} / {}). An unquoted ON or OFF in YAML is a "
                            + "boolean; quote it (\"ON\") or the interlocks will not recognise ON commands.",
                    properties.activatingCommands(),
                    properties.activeStates());
        }
        log.info(
                "Command safety: {} guard(s), activating={}, timedTypes={}, blockedOnBackupPower={}, exclusive={}, "
                        + "blockedWhileActive={}, signals={}",
                this.guards.size(),
                properties.activatingCommands(),
                properties.timedTypes(),
                properties.blockedOnBackupPowerTypes(),
                properties.exclusiveTypeGroups(),
                properties.blockedWhileActive(),
                properties.signalInterlocks().stream()
                        .map(signal -> signal.name() + "=" + signal.device() + "/" + signal.metricKey())
                        .toList());
    }

    /** Throws when any guard refuses; applies to operator, automation and system commands alike. */
    public void check(Actuator actuator, String command, Map<String, Object> parameters) {
        CommandContext context = new CommandContext(actuator, command, parameters, properties.isActivating(command));
        guards.forEach(guard -> guard.check(context));
    }

    /** Adds {@code expiresAt} when a command TTL is configured. */
    public OutboundCommand withExpiry(OutboundCommand command) {
        if (properties.commandTtl() == null || command.issuedAt() == null) {
            return command;
        }
        return command.expiringAt(command.issuedAt().plus(properties.commandTtl()));
    }
}
