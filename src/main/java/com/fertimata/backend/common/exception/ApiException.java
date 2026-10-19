package com.fertimata.backend.common.exception;

import java.util.Map;

/**
 * Base class for exceptions that map onto a documented API error.
 *
 * <p>Services throw subclasses of this instead of returning error codes, so controllers stay thin
 * and the mapping to HTTP lives in one place ({@link GlobalExceptionHandler}).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of(), null);
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, details, null);
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, Map.of(), cause);
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
