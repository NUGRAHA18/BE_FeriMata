package com.fertimata.backend.actuator.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The reason a device gave for refusing or failing a command.
 *
 * <p>The edge agent writes {@code errorMessage} as {@code "CODE: text for the farmer"}, for example
 * {@code "INTERLOCK_POWER: listrik PLN padam"} (MQTT contract §4.3). The code is machine-readable and
 * stable; the text is plain Indonesian meant to be shown as-is. A message without that prefix is
 * kept whole as the text, with no code, so older firmware still reads sensibly.
 *
 * <p>Known codes are not enumerated here on purpose: the edge owns that vocabulary and adds to it
 * (INTERLOCK_*, OUTPUT_FAULT, DEVICE_OFFLINE, ...), and an unknown code must not turn into a
 * rejected acknowledgement.
 *
 * @param code upper-case code, or null when the message carries none
 * @param text human-readable part, never null
 */
public record DeviceFailureReason(String code, String text) {

    public static final int MAX_CODE_LENGTH = 64;

    private static final Pattern CODED = Pattern.compile(
            "^([A-Z][A-Z0-9_]{0," + (MAX_CODE_LENGTH - 1) + "}):\\s*(.*)$", Pattern.DOTALL);

    public static DeviceFailureReason parse(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return new DeviceFailureReason(null, "Device reported failure");
        }
        String message = errorMessage.strip();
        Matcher matcher = CODED.matcher(message);
        if (!matcher.matches() || matcher.group(2).isBlank()) {
            return new DeviceFailureReason(null, message);
        }
        return new DeviceFailureReason(matcher.group(1), matcher.group(2).strip());
    }
}
