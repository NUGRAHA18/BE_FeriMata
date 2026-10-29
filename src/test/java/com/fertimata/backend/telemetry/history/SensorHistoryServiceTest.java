package com.fertimata.backend.telemetry.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.cultivation.CultivationProperties;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.retention.TelemetryRetentionProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensorHistoryServiceTest {

    /** Kamis, 10:20 WIB. */
    private static final Instant NOW = Instant.parse("2026-10-29T03:20:00Z");

    private static final long JAKARTA = Duration.ofHours(7).toSeconds();

    @Mock
    private SensorService sensorService;

    @Mock
    private SensorHistoryQueries queries;

    private SensorHistoryService service;

    @BeforeEach
    void setUp() {
        Sensor sensor = TestFixtures.sensor(5L, TestFixtures.device(1L, "RIO-TANK-01"), "EC-01", "tank.ec", "uS/cm");
        when(sensorService.require(5L)).thenReturn(sensor);
        service = new SensorHistoryService(
                sensorService,
                queries,
                new SensorHistoryProperties(
                        Duration.ofMinutes(15), Duration.ofMinutes(1), Duration.ofHours(2), Duration.ofDays(7), 300, 1000),
                new TelemetryRetentionProperties(
                        true, Duration.ofDays(30), Duration.ofDays(3), 5000, 40, Duration.ofHours(1), Duration.ofMinutes(2)),
                new CultivationProperties(ZoneId.of("Asia/Jakarta"), List.of()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("without a range or interval the last 7 days come back as hourly points from the rollup")
    void defaultsToSevenDaysHourly() {
        when(queries.buckets(anyLong(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(List.of(new SensorHistoryQueries.BucketRow(
                        Instant.parse("2026-10-22T03:00:00Z"), 360, BigDecimal.ONE, new BigDecimal("2"), BigDecimal.TEN)));

        SensorHistorySeriesResponse series = service.series(5L, null, null, null);

        assertThat(series.interval()).isEqualTo("1h");
        assertThat(series.from()).isEqualTo(Instant.parse("2026-10-22T03:00:00Z"));
        assertThat(series.to()).isEqualTo(NOW);
        assertThat(series.unit()).isEqualTo("uS/cm");
        assertThat(series.points()).singleElement().satisfies(p -> {
            assertThat(p.count()).isEqualTo(360);
            assertThat(p.avg()).isEqualTo(new BigDecimal("2"));
        });
        // Ringkasan sampai 2 jam (lookback) sebelum sekarang, sisanya data mentah.
        verify(queries)
                .buckets(5L, Instant.parse("2026-10-22T03:00:00Z"), NOW, Instant.parse("2026-10-29T01:00:00Z"), 3600, JAKARTA);
    }

    @Test
    @DisplayName("daily points start at midnight in the farm time zone, not UTC")
    void dailyPointsAlignToLocalMidnight() {
        SensorHistorySeriesResponse series = service.series(5L, Instant.parse("2026-10-01T00:00:00Z"), NOW, "1d");

        // 1 Okt 00:00 UTC = 07:00 WIB; titiknya mulai 1 Okt 00:00 WIB = 30 Sep 17:00 UTC.
        assertThat(series.from()).isEqualTo(Instant.parse("2026-09-30T17:00:00Z"));
    }

    @Test
    @DisplayName("weekly points start on Monday")
    void weeklyPointsStartOnMonday() {
        SensorHistorySeriesResponse series = service.series(5L, NOW, NOW, "7d");

        // Kamis 29 Okt WIB -> Senin 26 Okt 00:00 WIB.
        assertThat(series.from()).isEqualTo(Instant.parse("2026-10-25T17:00:00Z"));
    }

    @Test
    @DisplayName("a sub-hour interval inside the retention window reads only raw readings")
    void subHourUsesRawOnly() {
        Instant from = NOW.minus(Duration.ofHours(6));

        service.series(5L, from, NOW, "5m");

        verify(queries).buckets(5L, from, NOW, from, 300, JAKARTA);
    }

    @Test
    @DisplayName("a sub-hour interval reaching past the retention window is refused")
    void subHourBeyondRetentionIsRefused() {
        assertThatThrownBy(() -> service.series(5L, NOW.minus(Duration.ofDays(40)), NOW, "5m"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("30 days");
        verifyNoInteractions(queries);
    }

    @Test
    @DisplayName("the automatic interval skips sub-hour widths for data older than the retention window")
    void automaticIntervalForOldData() {
        SensorHistorySeriesResponse series = service.series(5L, NOW.minus(Duration.ofDays(40)), NOW, null);

        // 40 hari: 1h = 961 titik, 3h = 321, 6h = 161 <= 300.
        assertThat(series.interval()).isEqualTo("6h");
    }

    @Test
    @DisplayName("the automatic interval may pick a sub-hour width for a short recent range")
    void automaticIntervalForRecentData() {
        SensorHistorySeriesResponse series = service.series(5L, NOW.minus(Duration.ofHours(6)), NOW, null);

        // 6 jam: 1m = 361 titik, 5m = 73.
        assertThat(series.interval()).isEqualTo("5m");
    }

    @Test
    @DisplayName("an interval that would return too many points is refused")
    void tooManyPointsIsRefused() {
        assertThatThrownBy(() -> service.series(5L, NOW.minus(Duration.ofDays(7)), NOW, "1m"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limit is 1000");
    }

    @Test
    @DisplayName("an unknown interval lists the accepted ones")
    void unknownIntervalIsRefused() {
        assertThatThrownBy(() -> service.series(5L, null, null, "2h"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1m, 5m");
    }

    @Test
    @DisplayName("a range whose start is after its end is refused")
    void invertedRangeIsRefused() {
        assertThatThrownBy(() -> service.series(5L, NOW, NOW.minusSeconds(1), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("without retention every interval is allowed for any range")
    void noRetentionAllowsSubHourEverywhere() {
        SensorHistoryService unlimited = new SensorHistoryService(
                sensorService,
                queries,
                new SensorHistoryProperties(
                        Duration.ofMinutes(15), Duration.ofMinutes(1), Duration.ofHours(2), Duration.ofDays(7), 300, 1000),
                new TelemetryRetentionProperties(
                        false, Duration.ofDays(30), Duration.ofDays(3), 5000, 40, Duration.ofHours(1), Duration.ofMinutes(2)),
                new CultivationProperties(ZoneId.of("Asia/Jakarta"), List.of()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        Instant from = NOW.minus(Duration.ofDays(40)).minus(Duration.ofHours(1));

        unlimited.series(5L, from, from.plus(Duration.ofHours(1)), "15m");

        verify(queries).buckets(eq(5L), any(), any(), any(), eq(900L), eq(JAKARTA));
    }
}
