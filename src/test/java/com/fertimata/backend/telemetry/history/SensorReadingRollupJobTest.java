package com.fertimata.backend.telemetry.history;

import static org.mockito.Mockito.verify;

import com.fertimata.backend.telemetry.retention.TelemetryRetentionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorReadingRollupJobTest {

    private static final Instant NOW = Instant.parse("2026-10-29T03:20:00Z");

    @Mock
    private SensorHistoryQueries queries;

    private SensorReadingRollupJob job(boolean retentionEnabled) {
        return new SensorReadingRollupJob(
                queries,
                new SensorHistoryProperties(
                        Duration.ofMinutes(15), Duration.ofMinutes(1), Duration.ofHours(2), Duration.ofDays(7), 300, 1000),
                new TelemetryRetentionProperties(
                        retentionEnabled,
                        Duration.ofDays(30),
                        Duration.ofDays(3),
                        5000,
                        40,
                        Duration.ofHours(1),
                        Duration.ofMinutes(2)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("hours that received readings within the lookback are recomputed, never hours retention is purging")
    void recomputesRecentlyReceivedHoursAboveTheRetentionEdge() {
        job(true).runOnce(NOW);

        // Batas retensi 29 Sep 03:20 -> jam 03:00, plus dua jam pengaman.
        verify(queries)
                .rollUpReceivedSince(Instant.parse("2026-10-29T01:20:00Z"), Instant.parse("2026-09-29T05:00:00Z"));
    }

    @Test
    @DisplayName("without retention every hour may be recomputed")
    void withoutRetentionNothingIsOffLimits() {
        job(false).runOnce(NOW);

        verify(queries).rollUpReceivedSince(Instant.parse("2026-10-29T01:20:00Z"), Instant.EPOCH);
    }
}
