package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.common.exception.ErrorCode;
import java.util.Map;

/**
 * A command was well-formed but an interlock refused it. Maps to HTTP 409.
 *
 * <p>Nothing is recorded or published for a refused command: the plant state that caused the refusal
 * is already visible (a power alert, a running actuator), and the operator gets the reason directly.
 */
public class SafetyInterlockException extends ApiException {

    public SafetyInterlockException(String message, Map<String, Object> details) {
        super(ErrorCode.SAFETY_INTERLOCK, message, details);
    }
}
