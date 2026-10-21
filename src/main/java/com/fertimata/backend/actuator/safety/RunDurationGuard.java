package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.common.exception.InvalidCommandException;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Keeps activating commands bounded in time.
 *
 * <p>An actuator must carry a run time when its type is listed in {@code timed-types} or when it has
 * its own {@code maxRunSeconds}; the value must then be a whole number of seconds within that limit.
 * Rev A has no mechanical timer relay, so this limit, together with the edge agent's own watchdog, is
 * what keeps an output from running on when its OFF never arrives.
 */
@Component
@Order(10)
public class RunDurationGuard implements CommandGuard {

    private final ActuatorSafetyProperties properties;

    public RunDurationGuard(ActuatorSafetyProperties properties) {
        this.properties = properties;
    }

    @Override
    public void check(CommandContext context) {
        if (!context.activating()) {
            return;
        }
        Actuator actuator = context.actuator();
        String parameter = properties.durationParameter();
        Integer limit = actuator.getMaxRunSeconds();
        boolean timed = limit != null || ActuatorSafetyProperties.contains(properties.timedTypes(), actuator.getType());
        Object raw = context.parameters().get(parameter);

        if (raw == null) {
            if (timed) {
                throw new InvalidCommandException(
                        "Actuator %s must be commanded with %s".formatted(actuator.getCode(), parameter),
                        details(actuator, parameter, limit));
            }
            return;
        }

        long seconds = wholeSeconds(raw, actuator, parameter, limit);
        if (limit != null && seconds > limit) {
            throw new InvalidCommandException(
                    "%s=%d exceeds the %d second limit of actuator %s"
                            .formatted(parameter, seconds, limit, actuator.getCode()),
                    details(actuator, parameter, limit));
        }
    }

    private long wholeSeconds(Object raw, Actuator actuator, String parameter, Integer limit) {
        try {
            BigDecimal value = new BigDecimal(raw.toString().trim());
            long seconds = value.longValueExact();
            if (seconds > 0) {
                return seconds;
            }
        } catch (NumberFormatException | ArithmeticException ignored) {
            // Falls through to the rejection below.
        }
        throw new InvalidCommandException(
                "%s must be a positive whole number of seconds".formatted(parameter),
                details(actuator, parameter, limit));
    }

    private static Map<String, Object> details(Actuator actuator, String parameter, Integer limit) {
        return limit == null
                ? Map.of("actuatorCode", actuator.getCode(), "parameter", parameter)
                : Map.of("actuatorCode", actuator.getCode(), "parameter", parameter, "maxRunSeconds", limit);
    }
}
