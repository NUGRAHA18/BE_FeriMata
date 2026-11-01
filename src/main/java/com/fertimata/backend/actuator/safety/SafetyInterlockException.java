package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.common.exception.ErrorCode;
import java.util.Map;

/** Well-formed command refused by an interlock (HTTP 409); nothing is recorded or published for it. */
public class SafetyInterlockException extends ApiException {

    public SafetyInterlockException(String message, Map<String, Object> details) {
        super(ErrorCode.SAFETY_INTERLOCK, message, details);
    }
}
