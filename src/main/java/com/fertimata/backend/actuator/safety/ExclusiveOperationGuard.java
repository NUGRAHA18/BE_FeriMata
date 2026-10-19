package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Refuses to start an actuator while one of a conflicting type is running.
 *
 * <p>FERTIMATA Rev A asks for this between dosing and the camera trolley: together with the LED and
 * the pumps the DC bus approaches 100 W, and a trolley capture should not overlap a dosing cycle.
 * The groups are configuration.
 *
 * <p>"Running" is the state the device last <em>reported</em>. A report can be stale, so this guard
 * narrows the window rather than closing it; the edge agent owns the hard sequencing.
 */
@Component
@Order(40)
public class ExclusiveOperationGuard implements CommandGuard {

    private final ActuatorSafetyProperties properties;
    private final ActuatorRepository actuatorRepository;

    public ExclusiveOperationGuard(ActuatorSafetyProperties properties, ActuatorRepository actuatorRepository) {
        this.properties = properties;
        this.actuatorRepository = actuatorRepository;
    }

    @Override
    public void check(CommandContext context) {
        Actuator target = context.actuator();
        if (!context.activating() || target.getType() == null) {
            return;
        }
        List<List<String>> groups = properties.exclusiveTypeGroups().stream()
                .filter(group -> ActuatorSafetyProperties.contains(group, target.getType()))
                .toList();
        if (groups.isEmpty()) {
            return;
        }

        String targetType = ActuatorSafetyProperties.key(target.getType());
        Optional<Actuator> conflict = actuatorRepository.findAllWithDevice().stream()
                .filter(other -> !Objects.equals(other.getId(), target.getId()))
                .filter(other -> other.getType() != null)
                .filter(other -> !ActuatorSafetyProperties.key(other.getType()).equals(targetType))
                .filter(other -> groups.stream().anyMatch(group -> ActuatorSafetyProperties.contains(group, other.getType())))
                .filter(other -> properties.isActiveState(other.getCurrentState()))
                .findFirst();

        conflict.ifPresent(running -> {
            throw new SafetyInterlockException(
                    "%s (%s) cannot start while %s (%s) reports %s"
                            .formatted(target.getCode(), target.getType(), running.getCode(), running.getType(),
                                    running.getCurrentState()),
                    Map.of(
                            "interlock", "EXCLUSIVE_OPERATION",
                            "conflictingActuatorCode", running.getCode(),
                            "conflictingActuatorType", running.getType()));
        });
    }
}
