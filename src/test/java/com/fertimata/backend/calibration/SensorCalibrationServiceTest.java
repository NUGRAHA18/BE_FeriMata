package com.fertimata.backend.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.calibration.dto.CalibrationFitRequest;
import com.fertimata.backend.calibration.dto.CalibrationSampleRequest;
import com.fertimata.backend.calibration.dto.CalibrationSampleResponse;
import com.fertimata.backend.calibration.dto.DriftCheckRequest;
import com.fertimata.backend.calibration.dto.DriftCheckResponse;
import com.fertimata.backend.calibration.dto.SensorCalibrationResponse;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SensorCalibrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");

    @Mock
    private SensorService sensorService;

    @Mock
    private SensorCalibrationRepository calibrationRepository;

    @Mock
    private CalibrationSampleRepository sampleRepository;

    @Mock
    private SensorDriftCheckRepository driftCheckRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    @Mock
    private UserRepository userRepository;

    private SensorCalibrationService service;
    private Device device;
    private Sensor nitrogen;
    private Sensor moisture;

    @BeforeEach
    void setUp() {
        service = new SensorCalibrationService(
                sensorService,
                calibrationRepository,
                sampleRepository,
                driftCheckRepository,
                sensorReadingRepository,
                userRepository,
                new CalibrationProperties(Duration.ofMinutes(15), null, null, null, null),
                new JsonSupport(new ObjectMapper()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        device = TestFixtures.device(1L, "GREENHOUSE-01");
        nitrogen = TestFixtures.sensor(10L, device, "SOIL-A-N", "soil_a.nitrogen", "mg/kg");
        moisture = TestFixtures.sensor(11L, device, "SOIL-A-MOISTURE", "soil_a.moisture", "%");
        when(sensorService.require(10L)).thenReturn(nitrogen);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(sampleRepository.save(any(CalibrationSample.class))).thenAnswer(inv -> inv.getArgument(0));
        when(driftCheckRepository.save(any(SensorDriftCheck.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("a lab sample takes the sensor value and covariates from the same telemetry message")
    void sampleAutofill() {
        Instant recorded = NOW.minusSeconds(120);
        SensorReading n = reading(nitrogen, "212", recorded);
        when(sensorReadingRepository.findFirstBySensorIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(10L, NOW))
                .thenReturn(Optional.of(n));
        when(sensorReadingRepository.findByDeviceIdAndRecordedAt(1L, recorded))
                .thenReturn(List.of(n, reading(moisture, "41.2", recorded)));

        CalibrationSampleResponse sample = service.addSample(
                10L, new CalibrationSampleRequest(null, null, new BigDecimal("0.18"), "Kjeldahl", null, "LAB-1", null), "op");

        assertThat(sample.sensorValue()).isEqualByComparingTo("212");
        assertThat(sample.covariates()).containsOnlyKeys("soil_a.moisture");
        assertThat(sample.labMethod()).isEqualTo("Kjeldahl");
    }

    @Test
    @DisplayName("a stale reading is not used: the operator must enter the value")
    void sampleRefusesStaleReading() {
        when(sensorReadingRepository.findFirstBySensorIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(10L, NOW))
                .thenReturn(Optional.of(reading(nitrogen, "212", NOW.minus(Duration.ofHours(2)))));

        assertThatThrownBy(() -> service.addSample(
                        10L, new CalibrationSampleRequest(null, null, new BigDecimal("0.18"), null, null, null, null), "op"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("enter sensorValue by hand");
    }

    @Test
    @DisplayName("a drift check stores the deviation in units and percent")
    void driftDeviation() {
        DriftCheckResponse check = service.addDriftCheck(
                10L,
                new DriftCheckRequest(null, "EC 1413 uS/cm", new BigDecimal("1413"), new BigDecimal("1388"), true, null),
                "op");

        assertThat(check.deviation()).isEqualByComparingTo("-25");
        assertThat(check.deviationPercent()).isEqualByComparingTo("-1.7693");
        assertThat(check.probeCleaned()).isTrue();
        assertThat(check.checkedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("a fit preview is not stored; save = true stores it")
    void fitPreviewAndSave() {
        List<CalibrationSample> samples = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            CalibrationSample s = new CalibrationSample();
            s.setSensor(nitrogen);
            s.setSampledAt(NOW.minusSeconds(i * 3600L));
            s.setSensorValue(BigDecimal.valueOf(100L * i));
            s.setLabValue(BigDecimal.valueOf(0.05 + 0.001 * 100 * i));
            samples.add(s);
        }
        when(sampleRepository.findBySensorIdAndSampledAtBetweenOrderBySampledAtAsc(eq(10L), any(), any())).thenReturn(samples);
        when(calibrationRepository.save(any(SensorCalibration.class))).thenAnswer(inv -> {
            SensorCalibration c = inv.getArgument(0);
            TestFixtures.setId(c, 3L);
            when(calibrationRepository.findBySensorIdOrderByValidFromDesc(10L)).thenReturn(List.of(c));
            return c;
        });

        SensorCalibrationResponse preview =
                service.fit(10L, new CalibrationFitRequest(null, null, null, false, null, null), "op");
        assertThat(preview.id()).isNull();
        assertThat(preview.slope()).isEqualByComparingTo("0.001");
        assertThat(preview.intercept()).isEqualByComparingTo("0.05");
        assertThat(preview.method()).isEqualTo(CalibrationMethod.LINEAR);
        verify(calibrationRepository, never()).save(any());

        SensorCalibrationResponse saved =
                service.fit(10L, new CalibrationFitRequest(null, null, null, true, null, null), "op");
        assertThat(saved.id()).isEqualTo(3L);
        assertThat(saved.current()).isTrue();
        assertThat(saved.sampleCount()).isEqualTo(6);
    }

    private SensorReading reading(Sensor sensor, String value, Instant at) {
        SensorReading r = new SensorReading();
        r.setSensor(sensor);
        r.setDevice(device);
        r.setMetricKey(sensor.getMetricKey());
        r.setNumericValue(new BigDecimal(value));
        r.setRecordedAt(at);
        r.setReceivedAt(at);
        return r;
    }
}
