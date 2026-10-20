package com.fertimata.backend.cultivation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.cultivation.dto.CycleStatusResponse;
import com.fertimata.backend.cultivation.dto.GrowthPhaseRequest;
import com.fertimata.backend.cultivation.dto.PlantingCycleRequest;
import com.fertimata.backend.cultivation.dto.PlantingCycleResponse;
import com.fertimata.backend.plant.PlantRepository;
import com.fertimata.backend.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class CultivationServiceTest {

    /** 2026-10-20 00:30 WIB = 2026-10-19 17:30 UTC: the WIB date must win, not the UTC one. */
    private static final Instant NOW = Instant.parse("2026-10-19T17:30:00Z");
    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

    @Mock
    private PlantingCycleRepository cycleRepository;

    @Mock
    private PlantRepository plantRepository;

    private CultivationService service;

    @BeforeEach
    void setUp() {
        CultivationProperties properties = new CultivationProperties(
                WIB,
                List.of(
                        template("VEGETATIVE", 0, 24, "5.4", "6.3"),
                        template("YOUNG_FRUIT", 25, 44, "4.1", "7.9"),
                        template("FRUIT_ENLARGEMENT", 45, null, "5.4", "4.7")));
        service = new CultivationService(cycleRepository, plantRepository, properties, Clock.fixed(NOW, ZoneId.of("UTC")));
        when(cycleRepository.save(any(PlantingCycle.class))).thenAnswer(inv -> {
            PlantingCycle c = inv.getArgument(0);
            TestFixtures.setId(c, 1L);
            return c;
        });
    }

    @Test
    @DisplayName("HST counts days since transplant in WIB, and today's phase comes from the HST")
    void hstAndPhaseToday() {
        PlantingCycle cycle = new PlantingCycle("MT-2026-09", "Musim 1", LocalDate.of(2026, 9, 12));
        TestFixtures.setId(cycle, 1L);
        cycle.setStatus(CycleStatus.ACTIVE);
        cycle.replacePhases(service.create(new PlantingCycleRequest(
                                "TMP", "tmp", null, LocalDate.of(2026, 9, 12), null, null, false, null))
                        .phases().stream()
                        .map(p -> phase(p.code(), p.startHst(), p.endHst()))
                        .toList());
        when(cycleRepository.findFirstByStatus(CycleStatus.ACTIVE)).thenReturn(Optional.of(cycle));
        when(plantRepository.countByCycleIdAndActiveTrue(1L)).thenReturn(156L);
        when(plantRepository.countByCycleIdAndActiveTrueAndTreatmentCodeIsNotNull(1L)).thenReturn(48L);

        CycleStatusResponse status = service.activeStatus().orElseThrow();

        assertThat(status.today()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(status.hst()).isEqualTo(38);
        assertThat(status.currentPhase().code()).isEqualTo("YOUNG_FRUIT");
        assertThat(status.nextPhase().code()).isEqualTo("FRUIT_ENLARGEMENT");
        assertThat(status.daysUntilNextPhase()).isEqualTo(7);
        assertThat(status.plantCount()).isEqualTo(156);
        assertThat(status.treatmentPlantCount()).isEqualTo(48);
    }

    @Test
    @DisplayName("a cycle created without phases gets the configured template, and activating it ends the previous one")
    void createUsesTemplateAndEndsPreviousActive() {
        PlantingCycle previous = new PlantingCycle("OLD", "Lama", LocalDate.of(2026, 5, 1));
        previous.setStatus(CycleStatus.ACTIVE);
        when(cycleRepository.findFirstByStatus(CycleStatus.ACTIVE)).thenReturn(Optional.of(previous));

        PlantingCycleResponse created = service.create(new PlantingCycleRequest(
                "MT-2026-09", "Musim 1", "Netted", LocalDate.of(2026, 9, 12), null, null, null, null));

        assertThat(created.status()).isEqualTo(CycleStatus.ACTIVE);
        assertThat(created.phases()).extracting("code").containsExactly("VEGETATIVE", "YOUNG_FRUIT", "FRUIT_ENLARGEMENT");
        assertThat(created.phases().get(1).ratioK()).isEqualByComparingTo("7.9");
        assertThat(previous.getStatus()).isEqualTo(CycleStatus.ENDED);
        assertThat(previous.getEndedAt()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("overlapping or inverted phases are refused")
    void rejectsOverlappingPhases() {
        assertThatThrownBy(() -> CultivationService.validatePhases(
                        List.of(phase("A", 0, 30), phase("B", 25, 44))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");
        assertThatThrownBy(() -> CultivationService.validatePhases(List.of(phase("A", 10, 5))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("before it starts");
        assertThatThrownBy(() -> CultivationService.validatePhases(
                        List.of(phase("A", 0, null), phase("B", 50, null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    @DisplayName("gaps between phases are allowed and HST in a gap has no current phase")
    void gapsAllowed() {
        PlantingCycle cycle = new PlantingCycle("C", "c", LocalDate.of(2026, 10, 1));
        cycle.replacePhases(List.of(phase("A", 0, 5), phase("B", 10, null)));
        CultivationService.validatePhases(cycle.getPhases());
        assertThat(cycle.phaseAt(7)).isEmpty();
        assertThat(cycle.nextPhaseAfter(7).orElseThrow().getCode()).isEqualTo("B");
        assertThat(cycle.phaseAt(12).orElseThrow().getCode()).isEqualTo("B");
    }

    @Test
    @DisplayName("phase requests are normalised and validated")
    void replacePhasesFromRequests() {
        PlantingCycle cycle = new PlantingCycle("C", "c", LocalDate.of(2026, 10, 1));
        TestFixtures.setId(cycle, 7L);
        when(cycleRepository.findWithPhasesById(7L)).thenReturn(Optional.of(cycle));

        PlantingCycleResponse response = service.replacePhases(
                7L,
                List.of(
                        new GrowthPhaseRequest("late", "Akhir", 30, null, null, null, null, null, null, null),
                        new GrowthPhaseRequest("early", "Awal", 0, 29, new BigDecimal("1.5"), new BigDecimal("1.8"), null, null, null, null)));

        assertThat(response.phases()).extracting("code").containsExactly("EARLY", "LATE");
    }

    private static GrowthPhase phase(String code, int start, Integer end) {
        GrowthPhase p = new GrowthPhase();
        p.setCode(code);
        p.setName(code);
        p.setStartHst(start);
        p.setEndHst(end);
        return p;
    }

    private static CultivationProperties.PhaseTemplate template(String code, int start, Integer end, String n, String k) {
        return new CultivationProperties.PhaseTemplate(
                code, code, start, end, new BigDecimal("1.5"), new BigDecimal("2.0"), new BigDecimal(n), BigDecimal.ONE, new BigDecimal(k), null);
    }
}
