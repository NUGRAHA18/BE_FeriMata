package com.smartmelon.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kebijakan ingestion telemetri.
 *
 * @param autoRegisterSensors kalo nilainya true, metrik yang masuk dari sensor yang gak dikenal bakal bikin
 *     baris placeholder sensor berstatus disabled, bukannya langsung dibuang. Ini ngejaga biar data gak hilang
 *     pas tim hardware masih gonta-ganti set sensor; operator tinggal namain sama aktifin sensornya nanti.
 * @param maxClockSkew seberapa jauh timestamp dari device boleh mendahului waktu server sebelum akhirnya diganti
 *     pakai server receive time. Ini buat nahan device edge yang jamnya gak tersinkron.
 * @param maxMetricsPerMessage guard biar payload yang cacat atau berniat jahat gak meledak jadi berbaris-baris data.
 */

@ConfigurationProperties(prefix = "app.telemetry")
public record TelemetryProperties(
        boolean autoRegisterSensors, Duration maxClockSkew, int maxMetricsPerMessage) {}
