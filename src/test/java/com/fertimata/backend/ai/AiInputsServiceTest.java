package com.fertimata.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.cultivation.dto.CycleStatusResponse;
import com.fertimata.backend.cultivation.dto.GrowthPhaseResponse;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiInputsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");

    @Mock
    private SensorService sensorService;

    @Mock
    private CultivationService cultivationService;

    private AiInputsService service;

    @BeforeEach
    void setUp() {
        Map<String, String> fertilization = new LinkedHashMap<>();
        fertilization.put("ec", "tank.ec");
        fertilization.put("ph", "tank.ph");
        fertilization.put("nitrogen_tanah", "soil_a.nitrogen");
        Map<String, String> irrigation = new LinkedHashMap<>();
        irrigation.put("ec_air", "tank.ec");
        irrigation.put("suhu_udara", "air.temperature");
        service = new AiInputsService(
                sensorService,
                cultivationService,
                new AiInputsProperties(Duration.ofMinutes(15), fertilization, irrigation),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SensorReadingResponse reading(String metricKey, String value, String calibrated, Duration age) {
        return new SensorReadingResponse(
                1L, 1L, metricKey, metricKey, 1L, "RIO-TANK-01",
                new BigDecimal(value), calibrated == null ? null : new BigDecimal(calibrated), null, "u",
                NOW.minus(age), NOW.minus(age));
    }

    private void hst(long days) {
        GrowthPhaseResponse phase = new GrowthPhaseResponse(
                1L, "PEMBUAHAN", "Pembuahan", 35, 60, new BigDecimal("1.2"), new BigDecimal("2.5"), null);
        when(cultivationService.activeStatus()).thenReturn(Optional.of(
                new CycleStatusResponse(null, LocalDate.of(2026, 10, 29), days, phase, null, null, 1L)));
    }

    @Test
    @DisplayName("fresh readings fill the AI form fields by name, calibrated value first, plus HST")
    void freshReadingsAreReady() {
        hst(47);
        when(sensorService.latestPerSensor()).thenReturn(List.of(
                reading("tank.ec", "1.80", "1.75", Duration.ofMinutes(2)),
                reading("tank.ph", "6.1", null, Duration.ofMinutes(2)),
                reading("soil_a.nitrogen", "120", null, Duration.ofMinutes(5)),
                reading("air.temperature", "29.5", null, Duration.ofSeconds(30))));

        AiInputsResponse inputs = service.inputs();

        assertThat(inputs.readyForFertilization()).isTrue();
        assertThat(inputs.readyForIrrigation()).isTrue();
        assertThat(inputs.fertilization()).containsOnlyKeys("ec", "ph", "nitrogen_tanah", "hst");
        assertThat(inputs.fertilization().get("ec")).isEqualByComparingTo("1.75");
        assertThat(inputs.fertilization().get("hst")).isEqualByComparingTo("47");
        assertThat(inputs.irrigation().get("ec_air")).isEqualByComparingTo("1.75");
        assertThat(inputs.readings().get("tank.ec").calibrated()).isTrue();
        assertThat(inputs.readings().get("tank.ec").rawValue()).isEqualByComparingTo("1.80");
        assertThat(inputs.phaseName()).isEqualTo("Pembuahan");
        assertThat(inputs.problems()).isEmpty();
    }

    @Test
    @DisplayName("a stale or missing reading empties its field and blocks only the form that needs it")
    void staleAndMissingBlockTheForm() {
        hst(47);
        when(sensorService.latestPerSensor()).thenReturn(List.of(
                reading("tank.ec", "1.80", null, Duration.ofMinutes(30)),
                reading("air.temperature", "29.5", null, Duration.ofSeconds(30))));

        AiInputsResponse inputs = service.inputs();

        assertThat(inputs.readyForFertilization()).isFalse();
        assertThat(inputs.readyForIrrigation()).isFalse();
        assertThat(inputs.fertilization().get("ec")).isNull();
        assertThat(inputs.fertilization()).containsKey("ph").containsEntry("ph", null);
        assertThat(inputs.irrigation().get("suhu_udara")).isEqualByComparingTo("29.5");
        assertThat(inputs.readings().get("tank.ec").fresh()).isFalse();
        assertThat(inputs.readings().get("tank.ec").ageSeconds()).isEqualTo(1800);
        assertThat(inputs.problems())
                .contains("fertilization.ec (tank.ec): stale, 1800 s old")
                .contains("fertilization.ph (tank.ph): no reading");
    }

    @Test
    @DisplayName("without a transplant date no form is ready")
    void missingTransplantDate() {
        when(cultivationService.activeStatus()).thenReturn(Optional.empty());
        when(sensorService.latestPerSensor()).thenReturn(List.of(reading("air.temperature", "29.5", null, Duration.ZERO)));

        AiInputsResponse inputs = service.inputs();

        assertThat(inputs.hst()).isNull();
        assertThat(inputs.readyForIrrigation()).isFalse();
        assertThat(inputs.problems()).contains("irrigation.hst: transplant date is not set");
    }
}
