package com.fertimata.backend.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class ReadingCalibratorTest {

    private static final Instant T = Instant.parse("2026-10-20T03:00:00Z");

    @Mock
    private SensorCalibrationRepository calibrationRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    private ReadingCalibrator calibrator;
    private Device device;
    private Sensor nitrogen;
    private Sensor moisture;

    @BeforeEach
    void setUp() {
        calibrator = new ReadingCalibrator(
                calibrationRepository, sensorReadingRepository, new JsonSupport(new ObjectMapper()));
        device = TestFixtures.device(1L, "GREENHOUSE-01");
        nitrogen = TestFixtures.sensor(10L, device, "SOIL-A-N", "soil_a.nitrogen", "mg/kg");
        moisture = TestFixtures.sensor(11L, device, "SOIL-A-MOISTURE", "soil_a.moisture", "%");
    }

    @Test
    @DisplayName("without a calibration the calibrated value stays null")
    void noCalibration() {
        when(calibrationRepository.findBySensorIdInOrderByValidFromDesc(any())).thenReturn(List.of());

        List<SensorReadingResponse> out = calibrator.toResponses(List.of(reading(nitrogen, "200", T)));

        assertThat(out.get(0).value()).isEqualByComparingTo("200");
        assertThat(out.get(0).calibratedValue()).isNull();
    }

    @Test
    @DisplayName("each reading uses the calibration valid at its recordedAt")
    void calibrationValidAtReadingTime() {
        SensorCalibration old = calibration(1L, nitrogen, "0", "1", null, T.minusSeconds(3600));
        SensorCalibration recent = calibration(2L, nitrogen, "10", "0.5", null, T.plusSeconds(60));
        when(calibrationRepository.findBySensorIdInOrderByValidFromDesc(any())).thenReturn(List.of(recent, old));

        List<SensorReadingResponse> out = calibrator.toResponses(List.of(
                reading(nitrogen, "200", T), reading(nitrogen, "200", T.plusSeconds(120))));

        assertThat(out.get(0).calibratedValue()).isEqualByComparingTo("200");
        assertThat(out.get(1).calibratedValue()).isEqualByComparingTo("110");
    }

    @Test
    @DisplayName("covariates come from the same message in the list, without a database lookup")
    void covariateFromSameMessage() {
        SensorCalibration model = calibration(1L, nitrogen, "5", "0.8", "{\"soil_a.moisture\": -0.5}", T.minusSeconds(60));
        when(calibrationRepository.findBySensorIdInOrderByValidFromDesc(any())).thenReturn(List.of(model));

        List<SensorReadingResponse> out = calibrator.toResponses(List.of(
                reading(nitrogen, "100", T), reading(moisture, "40", T)));

        // 5 + 0,8 x 100 - 0,5 x 40
        assertThat(out.get(0).calibratedValue()).isEqualByComparingTo("65");
        assertThat(out.get(1).calibratedValue()).isNull();
        verify(sensorReadingRepository, never()).findByDeviceIdAndMetricKeyInAndRecordedAtIn(anyLong(), anyCollection(), anyCollection());
    }

    @Test
    @DisplayName("a covariate missing from the list is looked up; when absent the value stays null")
    void covariateLookedUp() {
        SensorCalibration model = calibration(1L, nitrogen, "5", "0.8", "{\"soil_a.moisture\": -0.5}", T.minusSeconds(60));
        when(calibrationRepository.findBySensorIdInOrderByValidFromDesc(any())).thenReturn(List.of(model));
        when(sensorReadingRepository.findByDeviceIdAndMetricKeyInAndRecordedAtIn(eq(1L), anyCollection(), anyCollection()))
                .thenReturn(List.of(reading(moisture, "20", T)));

        List<SensorReadingResponse> found = calibrator.toResponses(List.of(reading(nitrogen, "100", T)));
        assertThat(found.get(0).calibratedValue()).isEqualByComparingTo("75");

        when(sensorReadingRepository.findByDeviceIdAndMetricKeyInAndRecordedAtIn(eq(1L), anyCollection(), anyCollection()))
                .thenReturn(List.of());
        List<SensorReadingResponse> missing = calibrator.toResponses(List.of(reading(nitrogen, "100", T)));
        assertThat(missing.get(0).calibratedValue()).isNull();
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

    static SensorCalibration calibration(
            Long id, Sensor sensor, String intercept, String slope, String covariates, Instant validFrom) {
        SensorCalibration c = new SensorCalibration();
        TestFixtures.setId(c, id);
        c.setSensor(sensor);
        c.setMethod(covariates == null ? CalibrationMethod.LINEAR : CalibrationMethod.REGRESSION);
        c.setIntercept(new BigDecimal(intercept));
        c.setSlope(new BigDecimal(slope));
        c.setCovariates(covariates);
        c.setValidFrom(validFrom);
        return c;
    }
}
