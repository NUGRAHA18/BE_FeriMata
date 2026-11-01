package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** One-way exclusion, e.g. Rev A camera pan must not move while the trolley runs, but not vice versa.
 * "Running" is the last reported state, so this narrows the window; the edge owns hard sequencing. */
@Component
@Order(45)
public class BlockedWhileActiveGuard implements CommandGuard {

    private final ActuatorSafetyProperties properties;
    private final ActuatorRepository actuatorRepository;

    public BlockedWhileActiveGuard(ActuatorSafetyProperties properties, ActuatorRepository actuatorRepository) {
        this.properties = properties;
        this.actuatorRepository = actuatorRepository;
    }

    @Override
    public void check(CommandContext context) {
        Actuator target = context.actuator();
        if (!context.activating() || target.getType() == null) {
            return;
        }
        List<String> blockers = properties.blockedWhileActive().stream()
                .filter(rule -> ActuatorSafetyProperties.contains(rule.types(), target.getType()))
                .flatMap(rule -> rule.whileActive().stream())
                .toList();
        if (blockers.isEmpty()) {
            return;
        }

        Optional<Actuator> conflict = actuatorRepository.findAllWithDevice().stream()
                .filter(other -> !Objects.equals(other.getId(), target.getId()))
                .filter(other -> ActuatorSafetyProperties.contains(blockers, other.getType()))
                .filter(other -> properties.isActiveState(other.getCurrentState()))
                .findFirst();

        conflict.ifPresent(running -> {
            throw new SafetyInterlockException(
                    "%s (%s) cannot start while %s (%s) reports %s"
                            .formatted(target.getCode(), target.getType(), running.getCode(), running.getType(),
                                    running.getCurrentState()),
                    Map.of(
                            "interlock", "BLOCKED_WHILE_ACTIVE",
                            "conflictingActuatorCode", running.getCode(),
                            "conflictingActuatorType", running.getType()));
        });
    }
}
