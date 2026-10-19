package com.fertimata.backend.actuator.safety;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Interlocks applied before a command reaches the transport.
 *
 * <p>Every rule is configuration keyed by actuator <em>type</em> and command <em>name</em>, both of
 * which are free text owned by the hardware catalogue. No actuator type, command name or limit is
 * written in Java, so a different hardware revision changes this block and nothing else. With the
 * defaults below every guard is inert, which is exactly the phase-1 behaviour.
 *
 * <p>One rule holds regardless of configuration: guards only ever refuse <em>activating</em>
 * commands. A stop command is never blocked by an interlock.
 *
 * @param activatingCommands command names that switch something on; everything else counts as a stop
 * @param durationParameter parameter carrying the requested run time, in seconds
 * @param timedTypes actuator types that must always be commanded with a run time (relay timed mode)
 * @param blockedOnBackupPowerTypes actuator types refused while any device reports backup power
 * @param exclusiveTypeGroups groups of actuator types that must not run at the same time, for power
 *     budget or process reasons; actuators of the same type do not exclude each other
 * @param activeStates reported actuator states that count as running
 * @param refuseWhenDeviceOffline refuse activating commands for a device that is not ONLINE instead of
 *     letting the broker queue them
 * @param commandTtl how long a command stays valid on the wire; the edge agent must discard it after
 *     {@code expiresAt}. Null disables the expiry field
 */
@ConfigurationProperties(prefix = "app.actuator.safety")
public record ActuatorSafetyProperties(
        List<String> activatingCommands,
        String durationParameter,
        List<String> timedTypes,
        List<String> blockedOnBackupPowerTypes,
        List<List<String>> exclusiveTypeGroups,
        List<String> activeStates,
        boolean refuseWhenDeviceOffline,
        Duration commandTtl) {

    public ActuatorSafetyProperties {
        activatingCommands = normalise(activatingCommands, List.of("ON"));
        durationParameter = durationParameter == null || durationParameter.isBlank()
                ? "durationSeconds"
                : durationParameter.trim();
        timedTypes = normalise(timedTypes, List.of());
        blockedOnBackupPowerTypes = normalise(blockedOnBackupPowerTypes, List.of());
        exclusiveTypeGroups = exclusiveTypeGroups == null
                ? List.of()
                : exclusiveTypeGroups.stream().map(group -> normalise(group, List.of())).toList();
        activeStates = normalise(activeStates, List.of("ON"));
    }

    /** Settings with every guard inert; used where no configuration is bound, such as unit tests. */
    public static ActuatorSafetyProperties inert() {
        return new ActuatorSafetyProperties(null, null, null, null, null, null, false, null);
    }

    public boolean isActivating(String command) {
        return command != null && activatingCommands.contains(key(command));
    }

    public boolean isActiveState(String state) {
        return state != null && activeStates.contains(key(state));
    }

    static boolean contains(List<String> values, String candidate) {
        return candidate != null && values.contains(key(candidate));
    }

    static String key(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static List<String> normalise(List<String> values, List<String> fallback) {
        if (values == null) {
            return fallback;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(ActuatorSafetyProperties::key)
                .toList();
    }
}
