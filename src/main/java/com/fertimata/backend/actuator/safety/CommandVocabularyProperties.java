package com.fertimata.backend.actuator.safety;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which command names, and which parameters, each actuator type accepts.
 *
 * <p>Like the interlocks this is configuration owned by the hardware catalogue, keyed by actuator
 * type. A type that no vocabulary lists accepts any command name, which keeps actuators registered
 * by hand through the API working as before. Command names compare case-insensitively; parameter
 * names and text values are passed to the edge agent unchanged, so they compare exactly.
 *
 * @param vocabularies one entry per group of actuator types sharing the same commands
 */
@ConfigurationProperties(prefix = "app.actuator.commands")
public record CommandVocabularyProperties(List<Vocabulary> vocabularies) {

    public CommandVocabularyProperties {
        vocabularies = vocabularies == null
                ? List.of()
                : vocabularies.stream().filter(Objects::nonNull).toList();
    }

    /** No vocabulary at all: every command name is accepted. Used where nothing is bound. */
    public static CommandVocabularyProperties open() {
        return new CommandVocabularyProperties(null);
    }

    /** The vocabulary of an actuator type, if the catalogue restricts it. */
    public Optional<Vocabulary> forType(String type) {
        if (type == null) {
            return Optional.empty();
        }
        return vocabularies.stream()
                .filter(vocabulary -> ActuatorSafetyProperties.contains(vocabulary.types(), type))
                .findFirst();
    }

    /** Command names accepted by an actuator type, or null when the type is unrestricted. */
    public List<String> commandsFor(String type) {
        return forType(type).map(Vocabulary::commands).orElse(null);
    }

    /**
     * @param types actuator types this vocabulary applies to
     * @param commands accepted command names; anything else is refused with 400
     * @param parameters rules for parameters of specific commands; parameters not listed pass through
     */
    public record Vocabulary(List<String> types, List<String> commands, List<ParameterRule> parameters) {

        public Vocabulary {
            types = keys(types);
            commands = keys(commands);
            parameters = parameters == null
                    ? List.of()
                    : parameters.stream().filter(Objects::nonNull).toList();
        }

        public boolean accepts(String command) {
            return ActuatorSafetyProperties.contains(commands, command);
        }

        public List<ParameterRule> rulesFor(String command) {
            String key = ActuatorSafetyProperties.key(command);
            return parameters.stream().filter(rule -> key.equals(rule.command())).toList();
        }
    }

    /**
     * @param command the command this rule applies to
     * @param name parameter name, exactly as the edge agent reads it
     * @param kind {@code INTEGER}, {@code NUMBER} or {@code TEXT}
     * @param required defaults to true
     * @param min inclusive lower bound for numbers
     * @param max inclusive upper bound for numbers
     * @param allowedValues accepted values for text; empty accepts any non-blank text
     */
    public record ParameterRule(
            String command,
            String name,
            Kind kind,
            Boolean required,
            BigDecimal min,
            BigDecimal max,
            List<String> allowedValues) {

        public ParameterRule {
            command = command == null ? "" : ActuatorSafetyProperties.key(command);
            kind = kind == null ? Kind.TEXT : kind;
            required = required == null || required;
            allowedValues = allowedValues == null
                    ? List.of()
                    : allowedValues.stream().filter(Objects::nonNull).toList();
        }
    }

    public enum Kind {
        INTEGER,
        NUMBER,
        TEXT
    }

    private static List<String> keys(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .toList();
    }
}
