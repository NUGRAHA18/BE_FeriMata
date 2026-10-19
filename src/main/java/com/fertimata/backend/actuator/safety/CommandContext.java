package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import java.util.Map;

/**
 * What a guard needs to judge one command.
 *
 * @param actuator the target, with its device initialised
 * @param activating whether the command name is configured as one that switches something on
 */
public record CommandContext(Actuator actuator, String command, Map<String, Object> parameters, boolean activating) {

    public CommandContext {
        parameters = parameters == null ? Map.of() : parameters;
    }
}
