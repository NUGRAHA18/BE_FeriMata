package com.fertimata.backend.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.calibration.PumpCalibrationService;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceRepository;
import com.fertimata.backend.sensor.SensorRepository;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemMetricsServiceTest {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    // 21 Okt 2026 12.00 WIB s.d. 22 Okt 2026 12.00 WIB
    private static final Instant FROM = Instant.parse("2026-10-21T05:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-22T05:00:00Z");

    @Mock
    private SystemMetricsQueries queries;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private SensorRepository sensorRepository;

    @Mock
    private ActuatorRepository actuatorRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    @Mock
    private PumpCalibrationService pumpCalibrationService;

    @Mock
    private CultivationService cultivationService;

    private SystemMetricsService service;
    private Device greenhouse;

    @BeforeEach
    void setUp() {
        service = new SystemMetricsService(
                queries,
                deviceRepository,
                sensorRepository,
                actuatorRepository,
                sensorReadingRepository,
                pumpCalibrationService,
                cultivationService,
                new MetricsProperties(Duration.ofSeconds(60), Duration.ofMinutes(5), null, null),
                Clock.fixed(TO, ZoneOffset.UTC));
        greenhouse = TestFixtures.device(1L, "GREENHOUSE-01");
        when(cultivationService.zone()).thenReturn(WIB);
        when(deviceRepository.findAllByOrderByDeviceCodeAsc()).thenReturn(List.of(greenhouse, TestFixtures.device(2L, "PANEL-01")));
        when(sensorRepository.findAllWithDevice())
                .thenReturn(List.of(TestFixtures.sensor(10L, greenhouse, "AIR-TEMP", "air.temperature", "C")));
        when(actuatorRepository.findAllWithDevice()).thenReturn(List.of());
        when(pumpCalibrationService.dispensedByPumps(any(), any(), any())).thenReturn(List.of());
        when(sensorReadingRepository.findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtAsc(anyString(), any(), any()))
                .thenReturn(Optional.empty());
        when(sensorReadingRepository.findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtDesc(anyString(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("completeness, uptime and latency per reporting device; devices without sensors are left out")
    void deviceMetrics() {
        when(queries.telemetryPerDevice(FROM, TO, 300L))
                .thenReturn(List.of(new SystemMetricsQueries.DeviceTelemetryRow(1L, 1296, 216, 0.4, 1.25, 7.0)));
        when(queries.messagesPerDay(FROM, TO, WIB)).thenReturn(List.of(
                new SystemMetricsQueries.DailyRow(1L, LocalDate.of(2026, 10, 21), 720),
                new SystemMetricsQueries.DailyRow(1L, LocalDate.of(2026, 10, 22), 576)));
        when(queries.commandsPerActuator(FROM, TO)).thenReturn(List.of());

        SystemMetricsResponse metrics = service.metrics(FROM, TO);

        assertThat(metrics.devices()).hasSize(1);
        SystemMetricsResponse.DeviceMetrics device = metrics.devices().get(0);
        assertThat(device.messagesExpected()).isEqualTo(1440);
        assertThat(device.completenessPercent()).isEqualByComparingTo("90.00");
        assertThat(device.uptimePercent()).isEqualByComparingTo("75.00"); // 216 / 288 slot
        assertThat(device.latencyP95Seconds()).isEqualByComparingTo("1.250");
        // Hari pertama hanya 12 jam di dalam rentang: 720 pesan diharapkan, 720 diterima.
        assertThat(device.daily()).extracting(SystemMetricsResponse.DailyCompleteness::messagesExpected)
                .containsExactly(720L, 720L);
        assertThat(device.daily().get(0).completenessPercent()).isEqualByComparingTo("100.00");
        assertThat(device.daily().get(1).completenessPercent()).isEqualByComparingTo("80.00");
        assertThat(metrics.waterVolumeL()).isNull();
        assertThat(metrics.notes()).anyMatch(n -> n.contains("flow meter"));
    }

    @Test
    @DisplayName("actuation counts executed vs failed and leaves unacknowledged commands out of the success rate")
    void actuation() {
        when(queries.telemetryPerDevice(any(), any(), anyLong())).thenReturn(List.of());
        when(queries.messagesPerDay(any(), any(), any())).thenReturn(List.of());
        when(queries.commandsPerActuator(FROM, TO)).thenReturn(List.of(
                new SystemMetricsQueries.CommandRow(5L, "DIST-PUMP", "DISTRIBUTION_PUMP", "EXECUTED", 18),
                new SystemMetricsQueries.CommandRow(5L, "DIST-PUMP", "DISTRIBUTION_PUMP", "FAILED", 2),
                new SystemMetricsQueries.CommandRow(5L, "DIST-PUMP", "DISTRIBUTION_PUMP", "SENT", 3),
                new SystemMetricsQueries.CommandRow(6L, "DOSING-N", "DOSING_PUMP", "EXECUTED", 5)));

        SystemMetricsResponse.ActuationMetrics actuation = service.metrics(FROM, TO).actuation();

        assertThat(actuation.total()).isEqualTo(28);
        assertThat(actuation.executed()).isEqualTo(23);
        assertThat(actuation.unacknowledged()).isEqualTo(3);
        assertThat(actuation.successPercent()).isEqualByComparingTo("92.00");
        assertThat(actuation.perActuator()).hasSize(2);
    }

    @Test
    @DisplayName("energy is the rise of the cumulative counter; a counter reset is reported, not subtracted")
    void energy() {
        when(queries.telemetryPerDevice(any(), any(), anyLong())).thenReturn(List.of());
        when(queries.messagesPerDay(any(), any(), any())).thenReturn(List.of());
        when(queries.commandsPerActuator(any(), any())).thenReturn(List.of());
        when(sensorReadingRepository.findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtAsc("mains.energy", FROM, TO))
                .thenReturn(Optional.of(energy("120.500", FROM.plusSeconds(30))));
        when(sensorReadingRepository.findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtDesc("mains.energy", FROM, TO))
                .thenReturn(Optional.of(energy("123.250", TO.minusSeconds(30))));

        assertThat(service.metrics(FROM, TO).energy().consumedKwh()).isEqualByComparingTo("2.750");

        when(sensorReadingRepository.findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtDesc("mains.energy", FROM, TO))
                .thenReturn(Optional.of(energy("0.100", TO.minusSeconds(30))));
        SystemMetricsResponse reset = service.metrics(FROM, TO);
        assertThat(reset.energy().consumedKwh()).isNull();
        assertThat(reset.notes()).anyMatch(n -> n.contains("went down"));
    }

    @Test
    @DisplayName("a range longer than the limit is refused")
    void rangeLimit() {
        assertThatThrownBy(() -> service.metrics(TO.minus(Duration.ofDays(120)), TO))
                .isInstanceOf(BusinessRuleException.class);
    }

    private SensorReading energy(String kwh, Instant at) {
        SensorReading r = new SensorReading();
        r.setMetricKey("mains.energy");
        r.setNumericValue(new BigDecimal(kwh));
        r.setRecordedAt(at);
        return r;
    }
}
