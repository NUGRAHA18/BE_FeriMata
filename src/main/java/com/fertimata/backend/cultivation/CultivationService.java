package com.fertimata.backend.cultivation;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.cultivation.dto.CycleStatusResponse;
import com.fertimata.backend.cultivation.dto.GrowthPhaseRequest;
import com.fertimata.backend.cultivation.dto.GrowthPhaseResponse;
import com.fertimata.backend.cultivation.dto.PlantingCycleRequest;
import com.fertimata.backend.cultivation.dto.PlantingCycleResponse;
import com.fertimata.backend.cultivation.dto.PlantingCycleUpdateRequest;
import com.fertimata.backend.plant.PlantRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siklus tanam, fase pertumbuhan, dan jawaban "hari ini HST berapa, fase apa".
 *
 * <p>Backend hanya menghitung umur dan mencocokkan fase. Angka agronomi (batas HST, EC, rasio) datang
 * dari operator atau template konfigurasi dan tidak pernah dinilai di sini.
 */
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
    public List<PlantingCycleResponse> list() {
        return cycleRepository.findAllByOrderByTransplantDateDesc().stream()
                .map(PlantingCycleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlantingCycleResponse get(Long id) {
        return PlantingCycleResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public PlantingCycle require(Long id) {
        return cycleRepository.findWithPhasesById(id).orElseThrow(() -> new ResourceNotFoundException("PlantingCycle", id));
    }

    @Transactional(readOnly = true)
    public Optional<PlantingCycle> activeCycle() {
        return cycleRepository.findFirstByStatus(CycleStatus.ACTIVE);
    }

    /** Keadaan siklus aktif hari ini; kosong bila belum ada siklus aktif. */
    @Transactional(readOnly = true)
    public Optional<CycleStatusResponse> activeStatus() {
        return activeCycle().map(this::status);
    }

    @Transactional(readOnly = true)
    public CycleStatusResponse status(Long cycleId) {
        return status(require(cycleId));
    }

    private CycleStatusResponse status(PlantingCycle cycle) {
        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
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
                plantRepository.countByCycleIdAndActiveTrue(cycle.getId()),
                plantRepository.countByCycleIdAndActiveTrueAndTreatmentCodeIsNotNull(cycle.getId()));
    }

    /** HST hari ini untuk siklus tertentu, dipakai saat mencatat pengamatan. */
    public long hstToday(PlantingCycle cycle) {
        return cycle.hstOn(LocalDate.now(clock.withZone(properties.zone())));
    }

    @Transactional
    public PlantingCycleResponse create(PlantingCycleRequest request) {
        if (cycleRepository.existsByCode(request.code())) {
            throw new BusinessRuleException(
                    "A planting cycle with code %s already exists".formatted(request.code()),
                    Map.of("code", request.code()));
        }
        PlantingCycle cycle = new PlantingCycle(request.code(), request.name(), request.transplantDate());
        cycle.setVariety(request.variety());
        cycle.setExpectedHarvestDate(request.expectedHarvestDate());
        cycle.setNotes(request.notes());

        List<GrowthPhase> phases = request.phases() == null || request.phases().isEmpty()
                ? defaultPhases()
                : toPhases(request.phases());
        cycle.replacePhases(validatePhases(phases));

        boolean activate = request.activate() == null || request.activate();
        if (activate) {
            endCurrentActive();
            cycle.setStatus(CycleStatus.ACTIVE);
        } else {
            cycle.setStatus(CycleStatus.PLANNED);
        }

        PlantingCycle saved = cycleRepository.save(cycle);
        log.info(
                "Planting cycle {} created (transplant {}, {} phase(s), status {})",
                saved.getCode(),
                saved.getTransplantDate(),
                saved.getPhases().size(),
                saved.getStatus());
        return PlantingCycleResponse.from(saved);
    }

    @Transactional
    public PlantingCycleResponse update(Long id, PlantingCycleUpdateRequest request) {
        PlantingCycle cycle = require(id);
        if (request.name() != null) {
            cycle.setName(request.name());
        }
        if (request.variety() != null) {
            cycle.setVariety(request.variety());
        }
        if (request.transplantDate() != null) {
            cycle.setTransplantDate(request.transplantDate());
        }
        if (request.expectedHarvestDate() != null) {
            cycle.setExpectedHarvestDate(request.expectedHarvestDate());
        }
        if (request.notes() != null) {
            cycle.setNotes(request.notes());
        }
        if (request.status() != null && request.status() != cycle.getStatus()) {
            changeStatus(cycle, request.status());
        }
        return PlantingCycleResponse.from(cycle);
    }

    /** Mengganti seluruh daftar fase siklus; fase lama yang tidak ada lagi dihapus. */
    @Transactional
    public PlantingCycleResponse replacePhases(Long id, List<GrowthPhaseRequest> requests) {
        PlantingCycle cycle = require(id);
        List<GrowthPhase> phases = validatePhases(toPhases(requests));
        cycle.replacePhases(phases);
        log.info("Planting cycle {} now has {} phase(s)", cycle.getCode(), phases.size());
        return PlantingCycleResponse.from(cycle);
    }

    private void changeStatus(PlantingCycle cycle, CycleStatus target) {
        switch (target) {
            case ACTIVE -> {
                endCurrentActive();
                cycle.setStatus(CycleStatus.ACTIVE);
                cycle.setEndedAt(null);
            }
            case ENDED -> {
                cycle.setStatus(CycleStatus.ENDED);
                cycle.setEndedAt(LocalDate.now(clock.withZone(properties.zone())));
            }
            case PLANNED -> {
                cycle.setStatus(CycleStatus.PLANNED);
                cycle.setEndedAt(null);
            }
        }
        log.info("Planting cycle {} is now {}", cycle.getCode(), cycle.getStatus());
    }

    private void endCurrentActive() {
        cycleRepository.findFirstByStatus(CycleStatus.ACTIVE).ifPresent(active -> {
            active.setStatus(CycleStatus.ENDED);
            active.setEndedAt(LocalDate.now(clock.withZone(properties.zone())));
            log.info("Planting cycle {} ended because another cycle was activated", active.getCode());
        });
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
            phase.setRatioN(t.ratioN());
            phase.setRatioP(t.ratioP());
            phase.setRatioK(t.ratioK());
            phase.setNotes(t.notes());
            phases.add(phase);
        }
        return phases;
    }

    private static List<GrowthPhase> toPhases(List<GrowthPhaseRequest> requests) {
        List<GrowthPhase> phases = new ArrayList<>();
        for (GrowthPhaseRequest r : requests) {
            GrowthPhase phase = new GrowthPhase();
            phase.setCode(r.code().trim().toUpperCase());
            phase.setName(r.name().trim());
            phase.setStartHst(r.startHst());
            phase.setEndHst(r.endHst());
            phase.setEcTargetMin(r.ecTargetMin());
            phase.setEcTargetMax(r.ecTargetMax());
            phase.setRatioN(r.ratioN());
            phase.setRatioP(r.ratioP());
            phase.setRatioK(r.ratioK());
            phase.setNotes(r.notes());
            phases.add(phase);
        }
        return phases;
    }

    /**
     * Fase harus punya kode unik, rentang HST valid, dan tidak saling tumpang tindih. Celah di
     * antara fase diizinkan (HST tanpa fase hanya berarti "tidak ada target").
     *
     * @return salinan yang terurut menurut HST awal; masukan tidak diubah
     */
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
