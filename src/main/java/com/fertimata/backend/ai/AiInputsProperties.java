package com.fertimata.backend.ai;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Input fuzzy layanan AI ({@code GET /api/ai/inputs}): field form Swagger AI → metric key sensor, lewat konfigurasi.
 * Bacaan lebih tua dari {@code maxAge} dianggap basi (aturan keselamatan S9: jangan dosing dengan data basi). */
@Validated
@ConfigurationProperties(prefix = "app.ai.inputs")
public record AiInputsProperties(
        @NotNull Duration maxAge, @NotNull Map<String, String> fertilization, @NotNull Map<String, String> irrigation) {}
