package com.smartmelon.backend.security.config;

import com.smartmelon.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Merespons permintaan terautentikasi yang tidak memiliki peran (<i>role</i>) yang dibutuhkan.
 * */

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ApiErrorResponseWriter writer;

    public RestAccessDeniedHandler(ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(
        HttpServletRequest request, 
        HttpServletResponse response, 
        AccessDeniedException ex
    ) throws IOException {
        log.warn("Access denied for {} {}", request.getMethod(), request.getRequestURI());
        writer.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ErrorCode.ACCESS_DENIED,
                "You are not allowed to perform this action");
    }
}
