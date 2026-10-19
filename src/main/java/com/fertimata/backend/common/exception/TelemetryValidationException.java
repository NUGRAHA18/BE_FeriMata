package com.fertimata.backend.common.exception;

import java.util.Map;

/** Thrown when an inbound telemetry payload is unusable. */
public class TelemetryValidationException extends ApiException {

    public TelemetryValidationException(String message) {
        super(ErrorCode.INVALID_TELEMETRY, message);
    }

    public TelemetryValidationException(String message, Map<String, Object> details) {
        super(ErrorCode.INVALID_TELEMETRY, message, details);
    }
}
