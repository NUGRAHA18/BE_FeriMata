package com.fertimata.backend.security.config;

import com.fertimata.backend.common.exception.ErrorCode;
import com.fertimata.backend.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
        HttpServletRequest request, 
        HttpServletResponse response, 
        HttpStatus status, 
        ErrorCode code,
        String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(
            status.value(), 
            code.name(), 
            message, 
            request.getRequestURI(), 
            Map.of()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
