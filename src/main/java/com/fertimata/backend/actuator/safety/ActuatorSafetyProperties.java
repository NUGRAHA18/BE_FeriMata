package com.fertimata.backend.actuator.safety;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
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
 * @param blockedWhileActive one-directional exclusions: the listed types may not start while any of
 *     the other types runs, but not the other way round
 * @param signalInterlocks types refused unless a reported signal, such as a float switch, is in an
 *     allowed state
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
        Duration commandTtl,
        List<BlockedWhileActive> blockedWhileActive,
        List<SignalInterlock> signalInterlocks) {

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
        blockedWhileActive = blockedWhileActive == null
                ? List.of()
                : blockedWhileActive.stream().filter(Objects::nonNull).toList();
        signalInterlocks = signalInterlocks == null
                ? List.of()
                : signalInterlocks.stream().filter(Objects::nonNull).toList();
    }

    /** Settings with every guard inert; used where no configuration is bound, such as unit tests. */
    public static ActuatorSafetyProperties inert() {
        return new ActuatorSafetyProperties(null, null, null, null, null, null, false, null, null, null);
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

    /**
     * @param types actuator types that are refused
     * @param whileActive actuator types whose running state causes the refusal
     */
    public record BlockedWhileActive(List<String> types, List<String> whileActive) {

        public BlockedWhileActive {
            types = normalise(types, List.of());
            whileActive = normalise(whileActive, List.of());
        }
    }

    /**
     * @param name interlock name reported to the operator, for example {@code FLOAT_LOW}
     * @param device code of the device that reports the signal
     * @param metricKey telemetry key of the signal on that device
     * @param okValues reported values that allow the blocked types to run, compared case-insensitively;
     *     a numeric reading is compared without trailing zeros, so {@code 1.000} matches {@code "1"}
     * @param maxAge a reading older than this counts as missing; null accepts any age
     * @param blockedTypes actuator types refused while the signal is not OK, missing or stale
     */
    public record SignalInterlock(
            String name,
            String device,
            String metricKey,
            List<String> okValues,
            Duration maxAge,
            List<String> blockedTypes) {

        public SignalInterlock {
            name = name == null || name.isBlank() ? "SIGNAL" : key(name);
            okValues = normalise(okValues, List.of());
            blockedTypes = normalise(blockedTypes, List.of());
        }
    }
}
