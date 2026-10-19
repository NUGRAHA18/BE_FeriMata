package com.fertimata.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * Format error tunggal yang dipakai semua endpoint.
 *
 * <p>
 * Tanpa stack trace: {@code message} cuma ringkasan aman yang gampang dibaca,
 * {@code details} khusus nampung info error validasi di tingkat field.
 */

@Schema(name = "ApiError", description = "Standard error response")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        @Schema(example = "2026-09-15T04:41:07Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "VALIDATION_ERROR") String error,
        @Schema(example = "Invalid request") String message,
        @Schema(example = "/api/actuators/1/commands") String path,
        @Schema(description = "Field-level details, present for validation errors") Map<String, Object> details) {

    public static ApiError of(
            int status,
            String error,
            String message,
            String path,
            Map<String, Object> details) {
        return new ApiError(Instant.now(), status, error, message, path, details == null ? Map.of() : details);
    }
}
