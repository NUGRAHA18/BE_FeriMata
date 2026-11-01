package com.fertimata.backend.actuator.safety;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Accepted commands and parameters per actuator type (hardware catalogue); unlisted types accept any command.
 * Command names compare case-insensitively; parameter names and text values exactly, as the edge reads them. */
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

    /** Commands accepted by {@code types}, anything else is 400; parameters without a rule pass through. */
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

    /** {@code kind}: INTEGER, NUMBER or TEXT; {@code required} defaults to true; {@code min}/{@code max} inclusive;
     * empty {@code allowedValues} accepts any non-blank text. {@code name} is exactly what the edge reads. */
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
