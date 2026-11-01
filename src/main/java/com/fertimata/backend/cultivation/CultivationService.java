package com.fertimata.backend.cultivation;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.cultivation.dto.CycleStatusResponse;
import com.fertimata.backend.cultivation.dto.GrowthPhaseResponse;
import com.fertimata.backend.cultivation.dto.PlantingCycleResponse;
import com.fertimata.backend.plant.PlantRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HST dan fase hari ini dari satu tanggal tanam; satu siklus ACTIVE tersembunyi, diubah hanya lewat {@link #setSowingDate}. */
@Service
public class CultivationService {

    private static final Logger log = LoggerFactory.getLogger(CultivationService.class);

    private final PlantingCycleRepository cycleRepository;
    private final PlantRepository plantRepository;
    private final CultivationProperties properties;
    private final Clock clock;

    public CultivationService(
            PlantingCycleRepository cycleRepository,
            PlantRepository plantRepository,
            CultivationProperties properties,
            Clock clock) {
        this.cycleRepository = cycleRepository;
        this.plantRepository = plantRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<PlantingCycle> activeCycle() {
        return cycleRepository.findFirstByStatus(CycleStatus.ACTIVE);
    }

    /** Keadaan hari ini; kosong bila tanggal tanam pindah belum diisi. */
    @Transactional(readOnly = true)
    public Optional<CycleStatusResponse> activeStatus() {
        return activeCycle().map(this::status);
    }

    /** Tanggal hari ini di zona greenhouse. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }

    public ZoneId zone() {
        return properties.zone();
    }

    /** Satu-satunya pengaturan budidaya: tanggal tanam. Memperbarui siklus aktif atau membuatnya dengan template fase bawaan. */
    @Transactional
    public CycleStatusResponse setSowingDate(LocalDate sowingDate) {
        PlantingCycle cycle = activeCycle().orElseGet(() -> {
            String code = "MT-" + sowingDate;
            int n = 2;
            while (cycleRepository.existsByCode(code)) {
                code = "MT-" + sowingDate + "-" + n++;
            }
            PlantingCycle created = new PlantingCycle(code, "Musim tanam " + sowingDate, sowingDate);
            created.replacePhases(validatePhases(defaultPhases()));
            created.setStatus(CycleStatus.ACTIVE);
            return cycleRepository.save(created);
        });
        cycle.setSowingDate(sowingDate);
        log.info("Sowing date set to {} (cycle {})", sowingDate, cycle.getCode());
        return status(cycle);
    }

    /** Mengosongkan tanggal tanam saat tanaman terakhir dihapus (siklus selesai, riwayat tetap); true bila ada yang dikosongkan. */
    @Transactional
    public boolean resetSowingDate() {
        return activeCycle().map(cycle -> {
            cycle.setStatus(CycleStatus.ENDED);
            cycle.setEndedAt(today());
            log.info("Sowing date {} cleared (cycle {} ended)", cycle.getSowingDate(), cycle.getCode());
            return true;
        }).orElse(false);
    }

    private CycleStatusResponse status(PlantingCycle cycle) {
        LocalDate today = today();
        long hst = cycle.hstOn(today);
        Optional<GrowthPhase> current = cycle.phaseAt(hst);
        Optional<GrowthPhase> next = cycle.nextPhaseAfter(hst);
        return new CycleStatusResponse(
                PlantingCycleResponse.from(cycle),
                today,
                hst,
                current.map(GrowthPhaseResponse::from).orElse(null),
                next.map(GrowthPhaseResponse::from).orElse(null),
                next.map(p -> p.getStartHst() - hst).orElse(null),
                plantRepository.countByActiveTrue());
    }

    private List<GrowthPhase> defaultPhases() {
        List<GrowthPhase> phases = new ArrayList<>();
        for (CultivationProperties.PhaseTemplate t : properties.defaultPhases()) {
            GrowthPhase phase = new GrowthPhase();
            phase.setCode(t.code());
            phase.setName(t.name());
            phase.setStartHst(t.startHst());
            phase.setEndHst(t.endHst());
            phase.setEcTargetMin(t.ecTargetMin());
            phase.setEcTargetMax(t.ecTargetMax());
            phase.setNotes(t.notes());
            phases.add(phase);
        }
        return phases;
    }

    /** Validasi kode unik, rentang HST, dan tanpa tumpang tindih; mengembalikan salinan terurut HST awal. */
    static List<GrowthPhase> validatePhases(List<GrowthPhase> phases) {
        List<GrowthPhase> sorted = new ArrayList<>(phases);
        sorted.sort(Comparator.comparingInt(GrowthPhase::getStartHst));
        GrowthPhase previous = null;
        for (GrowthPhase phase : sorted) {
            if (phase.getEndHst() != null && phase.getEndHst() < phase.getStartHst()) {
                throw new BusinessRuleException(
                        "Phase %s ends (HST %d) before it starts (HST %d)"
                                .formatted(phase.getCode(), phase.getEndHst(), phase.getStartHst()),
                        Map.of("phase", phase.getCode()));
            }
            if (phase.getEcTargetMin() != null
                    && phase.getEcTargetMax() != null
                    && phase.getEcTargetMin().compareTo(phase.getEcTargetMax()) > 0) {
                throw new BusinessRuleException(
                        "Phase %s has ecTargetMin above ecTargetMax".formatted(phase.getCode()),
                        Map.of("phase", phase.getCode()));
            }
            if (previous != null) {
                if (previous.getCode().equals(phase.getCode())) {
                    throw new BusinessRuleException(
                            "Phase code %s is used twice".formatted(phase.getCode()), Map.of("phase", phase.getCode()));
                }
                if (previous.getEndHst() == null || previous.getEndHst() >= phase.getStartHst()) {
                    throw new BusinessRuleException(
                            "Phase %s overlaps phase %s".formatted(phase.getCode(), previous.getCode()),
                            Map.of("phase", phase.getCode()));
                }
            }
            previous = phase;
        }
        return sorted;
    }
}
