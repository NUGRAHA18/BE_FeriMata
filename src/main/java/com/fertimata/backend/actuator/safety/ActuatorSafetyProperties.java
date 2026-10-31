package com.fertimata.backend.actuator.safety;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Config-driven interlocks by actuator type and command name; no hardware specifics in Java, defaults inert.
 * Guards only refuse activating commands, never a stop; run time in seconds; null {@code commandTtl} = no expiry. */
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

    /** {@code types} are refused while any of {@code whileActive} runs (one direction only). */
    public record BlockedWhileActive(List<String> types, List<String> whileActive) {

        public BlockedWhileActive {
            types = normalise(types, List.of());
            whileActive = normalise(whileActive, List.of());
        }
    }

    /** Refuses {@code blockedTypes} unless {@code device}/{@code metricKey} is in {@code okValues} (case-insensitive,
     * 1.000 matches "1"); a missing reading or one older than {@code maxAge} (null = any age) counts as not OK. */
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
