package com.smartmelon.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Identifier error yang stabil dan machine-readable, dikembalikan di field {@code error} pada error API. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_COMMAND(HttpStatus.BAD_REQUEST),
    SAFETY_INTERLOCK(HttpStatus.CONFLICT),
    INVALID_TELEMETRY(HttpStatus.BAD_REQUEST),
    BUSINESS_RULE_VIOLATION(HttpStatus.CONFLICT),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    MESSAGING_ERROR(HttpStatus.SERVICE_UNAVAILABLE),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
