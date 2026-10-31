package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.common.exception.InvalidCommandException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Refuses command names/parameters the actuator type does not accept. Runs first, stops included (400), so a
 * typo like {@code GOTO} fails now instead of as a later {@code UNSUPPORTED} ACK from the edge. */
@Component
@Order(5)
public class CommandVocabularyGuard implements CommandGuard {

    private final CommandVocabularyProperties properties;

    public CommandVocabularyGuard(CommandVocabularyProperties properties) {
        this.properties = properties;
    }

    @Override
    public void check(CommandContext context) {
        Actuator actuator = context.actuator();
        Optional<CommandVocabularyProperties.Vocabulary> vocabulary = properties.forType(actuator.getType());
        if (vocabulary.isEmpty()) {
            return;
        }
        if (!vocabulary.get().accepts(context.command())) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("actuatorCode", actuator.getCode());
            details.put("command", String.valueOf(context.command()));
            details.put("supportedCommands", vocabulary.get().commands());
            throw new InvalidCommandException(
                    "Actuator %s (%s) does not accept command %s; supported: %s"
                            .formatted(actuator.getCode(), actuator.getType(), context.command(),
                                    String.join(", ", vocabulary.get().commands())),
                    details);
        }
        for (CommandVocabularyProperties.ParameterRule rule : vocabulary.get().rulesFor(context.command())) {
            checkParameter(actuator, rule, context.parameters().get(rule.name()));
        }
    }

    private static void checkParameter(Actuator actuator, CommandVocabularyProperties.ParameterRule rule, Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            if (rule.required()) {
                throw refusal(actuator, rule, "%s %s requires parameter %s"
                        .formatted(actuator.getCode(), rule.command(), rule.name()));
            }
            return;
        }
        switch (rule.kind()) {
            case INTEGER, NUMBER -> checkNumber(actuator, rule, raw);
            case TEXT -> checkText(actuator, rule, raw);
        }
    }

    private static void checkNumber(Actuator actuator, CommandVocabularyProperties.ParameterRule rule, Object raw) {
        boolean integer = rule.kind() == CommandVocabularyProperties.Kind.INTEGER;
        String expected = "%s must be %s".formatted(rule.name(), integer ? "a whole number" : "a number");
        // Text such as "2.2" is refused: the edge agent reads the JSON type, not a parsed string.
        if (!(raw instanceof Number)) {
            throw refusal(actuator, rule, expected);
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw.toString());
            if (integer) {
                value.longValueExact();
            }
        } catch (NumberFormatException | ArithmeticException ex) {
            throw refusal(actuator, rule, expected);
        }
        if ((rule.min() != null && value.compareTo(rule.min()) < 0)
                || (rule.max() != null && value.compareTo(rule.max()) > 0)) {
            throw refusal(actuator, rule, "%s=%s is outside %s..%s".formatted(rule.name(), value.toPlainString(),
                    rule.min() == null ? "" : rule.min().toPlainString(),
                    rule.max() == null ? "" : rule.max().toPlainString()));
        }
    }

    private static void checkText(Actuator actuator, CommandVocabularyProperties.ParameterRule rule, Object raw) {
        if (!(raw instanceof String text)) {
            throw refusal(actuator, rule, "%s must be text".formatted(rule.name()));
        }
        if (!rule.allowedValues().isEmpty() && !rule.allowedValues().contains(text)) {
            throw refusal(actuator, rule, "%s=%s is not one of %s"
                    .formatted(rule.name(), text, String.join(", ", rule.allowedValues())));
        }
    }

    private static InvalidCommandException refusal(
            Actuator actuator, CommandVocabularyProperties.ParameterRule rule, String message) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("actuatorCode", actuator.getCode());
        details.put("command", rule.command());
        details.put("parameter", rule.name());
        if (rule.min() != null) {
            details.put("min", rule.min());
        }
        if (rule.max() != null) {
            details.put("max", rule.max());
        }
        if (!rule.allowedValues().isEmpty()) {
            details.put("allowedValues", rule.allowedValues());
        }
        return new InvalidCommandException(message, details);
    }
}
