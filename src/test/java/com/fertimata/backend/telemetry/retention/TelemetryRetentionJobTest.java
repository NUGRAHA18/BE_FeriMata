package com.fertimata.backend.telemetry.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    @Mock
    private TelemetryRetentionService service;

    private TelemetryRetentionJob job(int batchSize, int maxBatches) {
        return new TelemetryRetentionJob(
                service,
                new TelemetryRetentionProperties(
                        true,
                        Duration.ofDays(30),
                        Duration.ofDays(3),
                        batchSize,
                        maxBatches,
                        Duration.ofHours(1),
                        Duration.ofMinutes(2)));
    }

    @Test
    @DisplayName("keeps deleting in batches until a batch comes back short, using the configured cut-offs")
    void drainsUntilShortBatch() {
        when(service.purgeReadingsBatch(eq(NOW.minus(Duration.ofDays(30))), eq(1000))).thenReturn(1000, 1000, 250);
        when(service.purgeMessagesBatch(eq(NOW.minus(Duration.ofDays(3))), eq(1000))).thenReturn(0);

        TelemetryRetentionJob.RunResult result = job(1000, 50).runOnce(NOW);

        assertThat(result.readings()).isEqualTo(2250);
        assertThat(result.messages()).isZero();
        assertThat(result.truncated()).isFalse();
        verify(service, times(3)).purgeReadingsBatch(any(), anyInt());
        verify(service, times(1)).purgeMessagesBatch(any(), anyInt());
    }

    @Test
    @DisplayName("stops after max-batches-per-run and reports that more remains")
    void stopsAtBatchCeiling() {
        when(service.purgeReadingsBatch(any(), anyInt())).thenReturn(500);
        when(service.purgeMessagesBatch(any(), anyInt())).thenReturn(0);

        TelemetryRetentionJob.RunResult result = job(500, 4).runOnce(NOW);

        assertThat(result.readings()).isEqualTo(2000);
        assertThat(result.truncated()).isTrue();
        verify(service, times(4)).purgeReadingsBatch(any(), anyInt());
    }
}
