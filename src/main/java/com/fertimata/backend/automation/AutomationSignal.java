package com.fertimata.backend.automation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single fact that a future rule engine could react to.
 *
 * <p>Kept free of telemetry and sensor types on purpose: the automation boundary must not depend on
 * the ingestion pipeline, otherwise changing one drags the other along.
 */
public record AutomationSignal(
        Long deviceId, String deviceCode, Long sensorId, String metricKey, BigDecimal value, Instant recordedAt) {}
