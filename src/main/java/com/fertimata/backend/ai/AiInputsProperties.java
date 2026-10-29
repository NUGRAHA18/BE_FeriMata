package com.fertimata.backend.ai;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Input untuk fuzzy layanan AI ({@code GET /api/ai/inputs}).
 *
 * <p>Nama field mengikuti form di Swagger layanan AI ({@code Ai-code/app/docs/swagger_docs.py}); nilainya
 * metric key sensor backend. Pemetaan ada di konfigurasi supaya bisa disesuaikan tanpa mengubah kode bila
 * tim AI mengganti arti sebuah field.
 *
 * @param maxAge bacaan lebih tua dari ini dianggap basi (aturan keselamatan S9: jangan dosing dengan data basi)
 * @param fertilization field form {@code run_fertilization} → metric key
 * @param irrigation field form {@code run_irrigation} → metric key
 */
@Validated
@ConfigurationProperties(prefix = "app.ai.inputs")
public record AiInputsProperties(
        @NotNull Duration maxAge, @NotNull Map<String, String> fertilization, @NotNull Map<String, String> irrigation) {}
