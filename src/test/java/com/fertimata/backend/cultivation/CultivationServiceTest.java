package com.fertimata.backend.cultivation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.cultivation.dto.CycleStatusResponse;
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
        // Sama dengan application.yml: FASE_MF fuzzy AI.
        CultivationProperties properties = new CultivationProperties(
                WIB,
                List.of(
                        template("SEMAI", 0, 7),
                        template("VEGETATIF", 8, 24),
                        template("PEMBUNGAAN", 25, 34),
                        template("PEMBUAHAN", 35, 60),
                        template("PEMATANGAN", 61, 74),
                        template("PANEN", 75, null)));
        service = new CultivationService(cycleRepository, plantRepository, properties, Clock.fixed(NOW, ZoneId.of("UTC")));
        when(cycleRepository.save(any(PlantingCycle.class))).thenAnswer(inv -> {
            PlantingCycle c = inv.getArgument(0);
            TestFixtures.setId(c, 1L);
            return c;
        });
    }

    @Test
    @DisplayName("HST counts days since sowing in WIB, and today's phase comes from the HST")
    void hstAndPhaseToday() {
        when(cycleRepository.findFirstByStatus(CycleStatus.ACTIVE)).thenReturn(Optional.empty());
        CycleStatusResponse created = service.setSowingDate(LocalDate.of(2026, 9, 12));
        when(plantRepository.countByActiveTrue()).thenReturn(5L);

        assertThat(created.today()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(created.hst()).isEqualTo(38);
        assertThat(created.currentPhase().code()).isEqualTo("PEMBUAHAN");
        assertThat(created.currentPhase().ecTargetMin()).isEqualByComparingTo("1.2");
        assertThat(created.nextPhase().code()).isEqualTo("PEMATANGAN");
        assertThat(created.daysUntilNextPhase()).isEqualTo(23);
    }

    @Test
    @DisplayName("setting the sowing date creates the greenhouse cycle with the template phases when none exists")
    void sowingDateCreatesCycle() {
        when(cycleRepository.findFirstByStatus(CycleStatus.ACTIVE)).thenReturn(Optional.empty());

        CycleStatusResponse status = service.setSowingDate(LocalDate.of(2026, 9, 12));

        assertThat(status.cycle().code()).isEqualTo("MT-2026-09-12");
        assertThat(status.cycle().status()).isEqualTo(CycleStatus.ACTIVE);
        assertThat(status.cycle().phases()).extracting("code")
                .containsExactly("SEMAI", "VEGETATIF", "PEMBUNGAAN", "PEMBUAHAN", "PEMATANGAN", "PANEN");
    }

    @Test
    @DisplayName("setting the sowing date again moves the existing cycle instead of starting a new one")
    void sowingDateUpdatesActiveCycle() {
        PlantingCycle active = new PlantingCycle("MT-2026-09-12", "Musim", LocalDate.of(2026, 9, 12));
        TestFixtures.setId(active, 1L);
        active.setStatus(CycleStatus.ACTIVE);
        active.replacePhases(List.of(phase("VEGETATIF", 8, 24), phase("PEMBUNGAAN", 25, 34)));
        when(cycleRepository.findFirstByStatus(CycleStatus.ACTIVE)).thenReturn(Optional.of(active));

        CycleStatusResponse status = service.setSowingDate(LocalDate.of(2026, 10, 1));

        assertThat(status.cycle().sowingDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(status.hst()).isEqualTo(19);
        assertThat(status.currentPhase().code()).isEqualTo("VEGETATIF");
        verify(cycleRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetting the sowing date ends the active cycle; the next date starts a new one")
    void resetEndsActiveCycle() {
        PlantingCycle active = new PlantingCycle("MT-2026-09-12", "Musim", LocalDate.of(2026, 9, 12));
        active.setStatus(CycleStatus.ACTIVE);
        when(cycleRepository.findFirstByStatus(CycleStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThat(service.resetSowingDate()).isTrue();
        assertThat(active.getStatus()).isEqualTo(CycleStatus.ENDED);
        assertThat(active.getEndedAt()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("without a sowing date there is no status")
    void noSowingDate() {
        when(cycleRepository.findFirstByStatus(CycleStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.activeStatus()).isEmpty();
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

    private static GrowthPhase phase(String code, int start, Integer end) {
        GrowthPhase p = new GrowthPhase();
        p.setCode(code);
        p.setName(code);
        p.setStartHst(start);
        p.setEndHst(end);
        return p;
    }

    private static CultivationProperties.PhaseTemplate template(String code, int start, Integer end) {
        return new CultivationProperties.PhaseTemplate(
                code, code, start, end, new BigDecimal("1.2"), new BigDecimal("2.5"), null);
    }
}
