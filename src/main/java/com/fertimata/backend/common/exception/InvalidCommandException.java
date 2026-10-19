package com.fertimata.backend.common.exception;

import java.util.Map;

/** Thrown when an actuator command cannot be accepted (unknown type, disabled actuator, ...). */
public class InvalidCommandException extends ApiException {

    public InvalidCommandException(String message) {
        super(ErrorCode.INVALID_COMMAND, message);
    }

    public InvalidCommandException(String message, Map<String, Object> details) {
        super(ErrorCode.INVALID_COMMAND, message, details);
    }
}
