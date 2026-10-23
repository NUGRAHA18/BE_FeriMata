package com.fertimata.backend.metrics;

import com.fertimata.backend.calibration.dto.DispensedVolumeResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Metrik teknis sistem untuk laporan (Protokol-Data.md §6). */
@Schema(name = "SystemMetrics")
public record SystemMetricsResponse(
        Instant from,
        Instant to,
        @Schema(description = "Expected telemetry interval used for completeness, seconds") long expectedIntervalSeconds,
        @Schema(description = "Uptime slot width, seconds") long uptimeBucketSeconds,
        List<DeviceMetrics> devices,
        ActuationMetrics actuation,
        EnergyMetrics energy,
        @Schema(description = "Nominal volume per calibrated pump") List<DispensedVolumeResponse> fertilizer,
        @Schema(description = "Null until a flow meter (water.flow) is installed") BigDecimal waterVolumeL,
        List<String> notes) {

    @Schema(name = "DeviceMetrics")
    public record DeviceMetrics(
            Long deviceId,
            String deviceCode,
            String deviceName,
            @Schema(description = "Distinct telemetry messages received") long messagesReceived,
            long messagesExpected,
            @Schema(description = "received / expected x 100; can exceed 100 when the device sends faster than expected")
                    BigDecimal completenessPercent,
            @Schema(description = "Share of uptime slots with at least one message, %") BigDecimal uptimePercent,
            @Schema(description = "Median of receivedAt - recordedAt, seconds") BigDecimal latencyP50Seconds,
            @Schema(description = "95th percentile of receivedAt - recordedAt, seconds") BigDecimal latencyP95Seconds,
            BigDecimal latencyMaxSeconds,
            List<DailyCompleteness> daily) {}

    @Schema(name = "DailyCompleteness")
    public record DailyCompleteness(
            @Schema(description = "Date in the greenhouse time zone") LocalDate date,
            long messagesReceived,
            long messagesExpected,
            BigDecimal completenessPercent) {}

    @Schema(name = "ActuationMetrics")
    public record ActuationMetrics(
            long total,
            long executed,
            long failed,
            @Schema(description = "PENDING or SENT: no acknowledgement yet") long unacknowledged,
            long cancelled,
            @Schema(description = "executed / (executed + failed) x 100; null when neither") BigDecimal successPercent,
            List<ActuatorActuation> perActuator) {}

    @Schema(name = "ActuatorActuation")
    public record ActuatorActuation(
            Long actuatorId,
            String actuatorCode,
            String actuatorType,
            long total,
            long executed,
            long failed,
            long unacknowledged,
            long cancelled) {}

    @Schema(name = "EnergyMetrics")
    public record EnergyMetrics(
            String metricKey,
            @Schema(description = "Last minus first cumulative reading in the range; null when unavailable or the counter reset")
                    BigDecimal consumedKwh,
            BigDecimal firstReadingKwh,
            Instant firstRecordedAt,
            BigDecimal lastReadingKwh,
            Instant lastRecordedAt) {}
}
