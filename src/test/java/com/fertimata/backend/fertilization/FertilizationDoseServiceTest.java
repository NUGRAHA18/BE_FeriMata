package com.fertimata.backend.fertilization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.calibration.ActuatorCalibration;
import com.fertimata.backend.calibration.PumpCalibrationService;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.fertilization.dto.FertilizationDoseRequest;
import com.fertimata.backend.fertilization.dto.FertilizationDoseResponse;
import com.fertimata.backend.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
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
class FertilizationDoseServiceTest {

    @Mock
    private FertilizationDoseSettingRepository repository;

    @Mock
    private ActuatorRepository actuatorRepository;

    @Mock
    private PumpCalibrationService calibrations;

    private FertilizationDoseService service;
    private final Map<String, Actuator> pumps = new HashMap<>();
    /** ml/s per actuator id; absent = not calibrated. */
    private final Map<Long, BigDecimal> flows = new HashMap<>();
    private Optional<FertilizationDoseSetting> stored = Optional.empty();

    @BeforeEach
    void setUp() {
        service = new FertilizationDoseService(repository, actuatorRepository, calibrations,
                new FertilizationProperties(true, Duration.ofSeconds(5), 120, null, null, null, null, null));
        long id = 1;
        for (String code : List.of("DOSING-N", "DOSING-P", "DOSING-K")) {
            Actuator pump = TestFixtures.actuator(id, TestFixtures.device(1L, "RIO-DOSING-01"), code);
            pump.setMaxRunSeconds(150);
            pumps.put(code, pump);
            flows.put(id, new BigDecimal("2.0"));
            id++;
        }
        lenient().when(actuatorRepository.findFirstByCode(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(pumps.get(inv.<String>getArgument(0))));
        lenient().when(calibrations.currentCalibration(anyLong())).thenAnswer(inv -> {
            BigDecimal flow = flows.get(inv.<Long>getArgument(0));
            if (flow == null) {
                return Optional.empty();
            }
            ActuatorCalibration calibration = new ActuatorCalibration();
            calibration.setFlowMlPerSecond(flow);
            return Optional.of(calibration);
        });
        lenient().when(repository.findFirstByOrderByIdAsc()).thenAnswer(inv -> stored);
        lenient().when(repository.save(any())).thenAnswer(inv -> {
            stored = Optional.of(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    private static FertilizationDoseRequest manual(String n, String p, String k) {
        return new FertilizationDoseRequest(FertilizationDoseSetting.Mode.MANUAL,
                n == null ? null : new BigDecimal(n), p == null ? null : new BigDecimal(p), k == null ? null : new BigDecimal(k));
    }

    private static String reason(Runnable call) {
        try {
            call.run();
        } catch (BusinessRuleException ex) {
            return String.valueOf(ex.details().get("reason"));
        }
        throw new AssertionError("expected a BusinessRuleException");
    }

    @Test
    @DisplayName("no row yet = AI; pumps show their calibration and most ml per run (150 s x 2 ml/s)")
    void defaultIsAi() {
        FertilizationDoseResponse dose = service.dose();

        assertThat(service.mode()).isEqualTo(FertilizationDoseSetting.Mode.AI);
        assertThat(dose.mode()).isEqualTo(FertilizationDoseSetting.Mode.AI);
        assertThat(dose.pumps()).extracting(FertilizationDoseResponse.Pump::nutrient).containsExactly("N", "P", "K");
        assertThat(dose.pumps().get(0).maxMl()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("manual ml are saved and turned into pump seconds with the latest calibration")
    void manualSaved() throws Exception {
        FertilizationDoseResponse dose = service.update(manual("50", "0", "0.5"), "operator");

        assertThat(dose.mode()).isEqualTo(FertilizationDoseSetting.Mode.MANUAL);
        assertThat(dose.updatedBy()).isEqualTo("operator");
        FertilizationDoseService.ManualDose seconds =
                service.manualDose(pumps.get("DOSING-N"), pumps.get("DOSING-P"), pumps.get("DOSING-K"));
        assertThat(seconds.nitrogenSeconds()).isEqualTo(25.0);
        assertThat(seconds.phosphorusSeconds()).isZero();
        // 0,5 ml = 0,25 s, still at least 1 s so it is not silently dropped.
        assertThat(seconds.potassiumSeconds()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("manual dose is refused when incomplete, all zero, uncalibrated, or more than one run pours")
    void manualRefused() {
        assertThat(reason(() -> service.update(manual("50", null, "10"), "op"))).isEqualTo("DOSE_MISSING");
        assertThat(reason(() -> service.update(manual("0", "0", "0"), "op"))).isEqualTo("DOSE_ZERO");
        assertThat(reason(() -> service.update(manual("301", "0", "0"), "op"))).isEqualTo("TOO_MUCH");
        flows.remove(2L);
        assertThat(reason(() -> service.update(manual("10", "10", "0"), "op"))).isEqualTo("PUMP_NOT_CALIBRATED");
        // An uncalibrated pump with 0 ml is fine.
        assertThat(service.update(manual("10", "0", "10"), "op").mode()).isEqualTo(FertilizationDoseSetting.Mode.MANUAL);
    }

    @Test
    @DisplayName("back to AI keeps the farmer's numbers for next time; AI mode never validates them")
    void backToAiKeepsNumbers() {
        service.update(manual("50", "20", "30"), "op");
        flows.clear();

        FertilizationDoseResponse dose =
                service.update(new FertilizationDoseRequest(FertilizationDoseSetting.Mode.AI, null, null, null), "op");

        assertThat(dose.mode()).isEqualTo(FertilizationDoseSetting.Mode.AI);
        assertThat(dose.nitrogenMl()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("a calibration removed after saving: the run is not poured (PUMP_NOT_CALIBRATED)")
    void calibrationGoneAtRunTime() {
        service.update(manual("50", "0", "0"), "op");
        flows.remove(1L);

        assertThatThrownBy(() -> service.manualDose(pumps.get("DOSING-N"), pumps.get("DOSING-P"), pumps.get("DOSING-K")))
                .isInstanceOf(FertilizationDoseService.DoseUnavailableException.class)
                .hasMessageContaining("DOSING-N");
        verify(repository, never()).delete(any());
    }
}
