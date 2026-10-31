package com.fertimata.backend.actuator.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed device {@code errorMessage} "CODE: text" (MQTT contract §4.3); without prefix the whole text, null code.
 * Codes are not enumerated: the edge owns that vocabulary and an unknown code must not reject the ACK. */
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
