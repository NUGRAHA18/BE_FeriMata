package com.fertimata.backend.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.cultivation.PlantingCycle;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.plant.dto.PlantObservationRequest;
import com.fertimata.backend.plant.dto.PlantObservationResponse;
import com.fertimata.backend.plant.dto.PlantRequest;
import com.fertimata.backend.plant.dto.PlantResponse;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.support.TestFixtures;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
class PlantServiceTest {

    @Mock
    private PlantRepository plantRepository;

    @Mock
    private PlantObservationRepository observationRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CultivationService cultivationService;

    private PlantService service;

    @BeforeEach
    void setUp() {
        service = new PlantService(
                plantRepository,
                observationRepository,
                sensorReadingRepository,
                userRepository,
                cultivationService,
                new JsonSupport(new ObjectMapper()));
        when(plantRepository.save(any(Plant.class))).thenAnswer(inv -> {
            Plant p = inv.getArgument(0);
            TestFixtures.setId(p, 10L);
            return p;
        });
        when(observationRepository.save(any(PlantObservation.class))).thenAnswer(inv -> {
            PlantObservation o = inv.getArgument(0);
            TestFixtures.setId(o, 99L);
            return o;
        });
        when(observationRepository.findFirstByPlantIdOrderByObservedAtDesc(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a plant defaults to the active cycle and codes are upper-cased")
    void createDefaultsToActiveCycle() {
        PlantingCycle cycle = new PlantingCycle("MT-2026-09", "Musim 1", LocalDate.of(2026, 9, 12));
        TestFixtures.setId(cycle, 1L);
        when(cultivationService.activeCycle()).thenReturn(Optional.of(cycle));

        PlantResponse created = service.create(new PlantRequest(
                "B1-N-02", null, null, null, "n-", "b1", "r1", "a", "st-03", 2, "fertimata", "1,3,5", null));

        assertThat(created.cycleCode()).isEqualTo("MT-2026-09");
        assertThat(created.name()).isEqualTo("B1-N-02");
        assertThat(created.treatmentCode()).isEqualTo("N-");
        assertThat(created.plotCode()).isEqualTo("A");
        assertThat(created.stationCode()).isEqualTo("ST-03");
        assertThat(created.comparisonGroup()).isEqualTo("FERTIMATA");
    }

    @Test
    @DisplayName("an observation records today's HST and a snapshot of the plot's soil sensors only")
    void observationSnapshotsSoilOfPlot() {
        PlantingCycle cycle = new PlantingCycle("MT", "m", LocalDate.of(2026, 9, 12));
        TestFixtures.setId(cycle, 1L);
        Plant plant = new Plant("B1-K0-01", "B1-K0-01");
        TestFixtures.setId(plant, 10L);
        plant.setCycle(cycle);
        plant.setPlotCode("A");
        when(plantRepository.findById(10L)).thenReturn(Optional.of(plant));
        when(cultivationService.hstToday(cycle)).thenReturn(38L);

        Device device = TestFixtures.device(1L, "GREENHOUSE-01");
        Sensor soilA = TestFixtures.sensor(1L, device, "SOIL-A-PH", "soil_a.ph", "pH");
        Sensor soilB = TestFixtures.sensor(2L, device, "SOIL-B-PH", "soil_b.ph", "pH");
        Sensor air = TestFixtures.sensor(3L, device, "AIR-TEMP", "air.temperature", "C");
        when(sensorReadingRepository.findLatestPerSensor())
                .thenReturn(List.of(reading(soilA, "6.4"), reading(soilB, "6.1"), reading(air, "29.5")));

        PlantObservationResponse response = service.addObservation(
                10L,
                new PlantObservationRequest(null, new BigDecimal("48.5"), 11, new BigDecimal("14.2"), new BigDecimal("12.8"), "menguning merata", null),
                "operator");

        assertThat(response.hst()).isEqualTo(38);
        assertThat(response.heightCm()).isEqualByComparingTo("48.5");
        assertThat(response.soilSnapshot()).containsOnlyKeys("soil_a.ph");
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) response.soilSnapshot().get("soil_a.ph");
        assertThat(ph.get("unit")).isEqualTo("pH");
    }

    @Test
    @DisplayName("a plant without a plot gets no soil snapshot and no HST without a cycle")
    void observationWithoutPlotOrCycle() {
        Plant plant = new Plant("X", "X");
        TestFixtures.setId(plant, 10L);
        when(plantRepository.findById(10L)).thenReturn(Optional.of(plant));

        PlantObservationResponse response = service.addObservation(
                10L, new PlantObservationRequest(null, null, 5, null, null, null, null), null);

        assertThat(response.hst()).isNull();
        assertThat(response.soilSnapshot()).isEmpty();
        assertThat(response.observedBy()).isNull();
    }

    private static SensorReading reading(Sensor sensor, String value) {
        SensorReading r = new SensorReading();
        r.setSensor(sensor);
        r.setDevice(sensor.getDevice());
        r.setMetricKey(sensor.getMetricKey());
        r.setNumericValue(new BigDecimal(value));
        r.setUnit(sensor.getUnit());
        r.setRecordedAt(Instant.parse("2026-10-20T03:00:00Z"));
        r.setReceivedAt(r.getRecordedAt());
        return r;
    }
}
