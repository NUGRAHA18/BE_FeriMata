package com.fertimata.backend.common.exception;

import java.util.Map;

/** Thrown when a request is syntactically valid but violates a domain rule. Maps to HTTP 409. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public BusinessRuleException(String message, Map<String, Object> details) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message, details);
    }
}
