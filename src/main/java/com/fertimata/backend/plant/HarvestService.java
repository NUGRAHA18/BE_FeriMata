package com.fertimata.backend.plant;

import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.plant.dto.HarvestRequest;
import com.fertimata.backend.plant.dto.HarvestResponse;
import com.fertimata.backend.user.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Data panen per buah (Protokol-Data.md §6). */
@Service
public class HarvestService {

    private static final Logger log = LoggerFactory.getLogger(HarvestService.class);

    private final HarvestRecordRepository harvestRepository;
    private final PlantService plantService;
    private final CultivationService cultivationService;
    private final UserRepository userRepository;

    public HarvestService(
            HarvestRecordRepository harvestRepository,
            PlantService plantService,
            CultivationService cultivationService,
            UserRepository userRepository) {
        this.harvestRepository = harvestRepository;
        this.plantService = plantService;
        this.cultivationService = cultivationService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<HarvestResponse> forPlant(Long plantId) {
        plantService.require(plantId);
        return harvestRepository.findByPlantIdOrderByHarvestedOnDescIdDesc(plantId).stream()
                .map(HarvestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HarvestResponse> search(PlantFilter filter) {
        return find(filter).stream().map(HarvestResponse::from).toList();
    }

    /** Untuk ekspor: entitas dengan tanaman sudah dimuat. */
    @Transactional(readOnly = true)
    public List<HarvestRecord> find(PlantFilter filter) {
        Sort sort = Sort.by("harvestedOn", "id");
        return harvestRepository.findAll(matching(filter), sort);
    }

    @Transactional
    public HarvestResponse add(Long plantId, HarvestRequest request, String username) {
        Plant plant = plantService.require(plantId);
        HarvestRecord harvest = new HarvestRecord();
        harvest.setPlant(plant);
        LocalDate date = request.harvestedOn() == null ? cultivationService.today() : request.harvestedOn();
        harvest.setHarvestedOn(date);
        harvest.setHst(plant.getCycle() == null ? null : (int) plant.getCycle().hstOn(date));
        harvest.setFruitWeightG(request.fruitWeightG());
        harvest.setBrix(request.brix());
        harvest.setFirmness(request.firmness());
        harvest.setFirmnessUnit(request.firmnessUnit() == null || request.firmnessUnit().isBlank() ? null : request.firmnessUnit().trim());
        harvest.setFleshThicknessMm(request.fleshThicknessMm());
        harvest.setDryMatterPercent(request.dryMatterPercent());
        harvest.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        if (username != null) {
            harvest.setRecordedBy(userRepository.findByUsername(username).orElse(null));
        }
        HarvestRecord saved = harvestRepository.save(harvest);
        log.info("Harvest recorded for plant {} on {} ({} g, {} Brix)", plant.getCode(), date, saved.getFruitWeightG(), saved.getBrix());
        return HarvestResponse.from(saved);
    }

    private static Specification<HarvestRecord> matching(PlantFilter f) {
        return (root, query, cb) -> {
            // Tanaman selalu dibaca (kode, perlakuan): fetch join yang sama dipakai untuk menyaring,
            // daripada satu query per baris. Aman karena spesifikasi ini tidak dipakai untuk paging/count.
            @SuppressWarnings("unchecked")
            Join<HarvestRecord, Plant> plant = (Join<HarvestRecord, Plant>) root.<HarvestRecord, Plant>fetch("plant");
            List<Predicate> predicates = new ArrayList<>();
            if (f != null) {
                if (f.cycleId() != null) {
                    predicates.add(cb.equal(plant.get("cycle").get("id"), f.cycleId()));
                }
                if (f.treatmentCode() != null) {
                    predicates.add(cb.equal(plant.get("treatmentCode"), f.treatmentCode().trim().toUpperCase()));
                }
                if (f.blockCode() != null) {
                    predicates.add(cb.equal(plant.get("blockCode"), f.blockCode().trim().toUpperCase()));
                }
                if (f.comparisonGroup() != null) {
                    predicates.add(cb.equal(plant.get("comparisonGroup"), f.comparisonGroup().trim().toUpperCase()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
