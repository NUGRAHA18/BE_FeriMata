package com.fertimata.backend.plant;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.cultivation.PlantingCycle;
import com.fertimata.backend.plant.dto.PlantObservationRequest;
import com.fertimata.backend.plant.dto.PlantObservationResponse;
import com.fertimata.backend.plant.dto.PlantRequest;
import com.fertimata.backend.plant.dto.PlantResponse;
import com.fertimata.backend.plant.dto.PlantUpdateRequest;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registri tanaman perlakuan dan log pengamatannya. */
@Service
public class PlantService {

    private static final Logger log = LoggerFactory.getLogger(PlantService.class);

    private final PlantRepository plantRepository;
    private final PlantObservationRepository observationRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final UserRepository userRepository;
    private final CultivationService cultivationService;
    private final JsonSupport jsonSupport;

    public PlantService(
            PlantRepository plantRepository,
            PlantObservationRepository observationRepository,
            SensorReadingRepository sensorReadingRepository,
            UserRepository userRepository,
            CultivationService cultivationService,
            JsonSupport jsonSupport) {
        this.plantRepository = plantRepository;
        this.observationRepository = observationRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.userRepository = userRepository;
        this.cultivationService = cultivationService;
        this.jsonSupport = jsonSupport;
    }

    @Transactional(readOnly = true)
    public List<PlantResponse> list(PlantFilter filter) {
        Sort sort = Sort.by("blockCode", "treatmentCode", "code");
        return plantRepository.findAll(matching(filter), sort).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlantResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public Plant require(Long id) {
        return plantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plant", id));
    }

    @Transactional
    public PlantResponse create(PlantRequest request) {
        if (plantRepository.existsByCode(request.code())) {
            throw new BusinessRuleException(
                    "A plant with code %s already exists".formatted(request.code()), Map.of("code", request.code()));
        }
        Plant plant = new Plant(request.code(), blankToNull(request.name()) == null ? request.code() : request.name().trim());
        plant.setDescription(blankToNull(request.description()));
        plant.setCycle(resolveCycle(request.cycleId()));
        plant.setTreatmentCode(upper(request.treatmentCode()));
        plant.setBlockCode(upper(request.blockCode()));
        plant.setRowCode(upper(request.rowCode()));
        plant.setPlotCode(upper(request.plotCode()));
        plant.setStationCode(upper(request.stationCode()));
        plant.setPositionInRow(request.positionInRow());
        plant.setComparisonGroup(upper(request.comparisonGroup()));
        plant.setTaggedLeaves(blankToNull(request.taggedLeaves()));
        plant.setMetadata(jsonSupport.toJson(request.metadata()));
        Plant saved = plantRepository.save(plant);
        log.info("Plant {} registered (treatment {}, block {})", saved.getCode(), saved.getTreatmentCode(), saved.getBlockCode());
        return toResponse(saved);
    }

    /** Mendaftarkan banyak tanaman sekaligus; satu kode duplikat menggagalkan seluruh batch. */
    @Transactional
    public List<PlantResponse> createBatch(List<PlantRequest> requests) {
        List<PlantResponse> created = new ArrayList<>();
        for (PlantRequest request : requests) {
            created.add(create(request));
        }
        return created;
    }

    @Transactional
    public PlantResponse update(Long id, PlantUpdateRequest request) {
        Plant plant = require(id);
        if (request.name() != null) {
            plant.setName(request.name());
        }
        if (request.description() != null) {
            plant.setDescription(blankToNull(request.description()));
        }
        if (request.cycleId() != null) {
            plant.setCycle(cultivationService.require(request.cycleId()));
        }
        if (request.treatmentCode() != null) {
            plant.setTreatmentCode(upper(request.treatmentCode()));
        }
        if (request.blockCode() != null) {
            plant.setBlockCode(upper(request.blockCode()));
        }
        if (request.rowCode() != null) {
            plant.setRowCode(upper(request.rowCode()));
        }
        if (request.plotCode() != null) {
            plant.setPlotCode(upper(request.plotCode()));
        }
        if (request.stationCode() != null) {
            plant.setStationCode(upper(request.stationCode()));
        }
        if (request.positionInRow() != null) {
            plant.setPositionInRow(request.positionInRow());
        }
        if (request.comparisonGroup() != null) {
            plant.setComparisonGroup(upper(request.comparisonGroup()));
        }
        if (request.taggedLeaves() != null) {
            plant.setTaggedLeaves(blankToNull(request.taggedLeaves()));
        }
        if (request.active() != null) {
            plant.setActive(request.active());
        }
        if (request.metadata() != null) {
            plant.setMetadata(jsonSupport.toJson(request.metadata()));
        }
        return toResponse(plant);
    }

    // ── Pengamatan ────────────────────────────────────────────────────────────

    @Transactional
    public PlantObservationResponse addObservation(Long plantId, PlantObservationRequest request, String username) {
        Plant plant = require(plantId);
        PlantObservation observation = new PlantObservation();
        observation.setPlant(plant);
        observation.setObservedAt(request.observedAt() == null ? Instant.now() : request.observedAt());
        observation.setHst(plant.getCycle() == null ? null : (int) cultivationService.hstToday(plant.getCycle()));
        observation.setHeightCm(request.heightCm());
        observation.setLeafCount(request.leafCount());
        observation.setLeaf3LengthCm(request.leaf3LengthCm());
        observation.setLeaf3WidthCm(request.leaf3WidthCm());
        observation.setVisualNotes(blankToNull(request.visualNotes()));
        if (request.snapshotSoil() == null || request.snapshotSoil()) {
            Map<String, Object> snapshot = soilSnapshot(plant);
            observation.setSoilSnapshot(snapshot.isEmpty() ? null : jsonSupport.toJson(snapshot));
        }
        if (username != null) {
            observation.setObservedBy(userRepository.findByUsername(username).orElse(null));
        }
        PlantObservation saved = observationRepository.save(observation);
        log.info("Observation recorded for plant {} (HST {})", plant.getCode(), saved.getHst());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlantObservationResponse> observations(Long plantId, Pageable pageable) {
        require(plantId);
        return PageResponse.from(observationRepository.findByPlantIdOrderByObservedAtDesc(plantId, pageable), this::toResponse);
    }

    /**
     * Pembacaan terakhir sensor tanah plot tanaman ini, sebagai {@code metricKey -> {value, unit, recordedAt}}.
     * Kosong bila belum ada pembacaan.
     */
    Map<String, Object> soilSnapshot(Plant plant) {
        String prefix = plant.soilMetricPrefix();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (SensorReading reading : sensorReadingRepository.findLatestPerSensor()) {
            if (!reading.getMetricKey().startsWith(prefix)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", reading.getNumericValue() != null ? reading.getNumericValue() : reading.getTextValue());
            entry.put("unit", reading.getUnit());
            entry.put("recordedAt", reading.getRecordedAt().toString());
            snapshot.put(reading.getMetricKey(), entry);
        }
        return snapshot;
    }

    private PlantingCycle resolveCycle(Long cycleId) {
        if (cycleId != null) {
            return cultivationService.require(cycleId);
        }
        return cultivationService.activeCycle().orElse(null);
    }

    private PlantResponse toResponse(Plant plant) {
        PlantObservationResponse last = observationRepository
                .findFirstByPlantIdOrderByObservedAtDesc(plant.getId())
                .map(this::toResponse)
                .orElse(null);
        return PlantResponse.from(plant, jsonSupport.toMap(plant.getMetadata()), last);
    }

    private PlantObservationResponse toResponse(PlantObservation observation) {
        return PlantObservationResponse.from(observation, jsonSupport.toMap(observation.getSoilSnapshot()));
    }

    private static Specification<Plant> matching(PlantFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f != null) {
                if (f.cycleId() != null) {
                    predicates.add(cb.equal(root.get("cycle").get("id"), f.cycleId()));
                }
                if (f.treatmentCode() != null) {
                    predicates.add(cb.equal(root.get("treatmentCode"), f.treatmentCode().trim().toUpperCase()));
                }
                if (f.blockCode() != null) {
                    predicates.add(cb.equal(root.get("blockCode"), f.blockCode().trim().toUpperCase()));
                }
                if (f.stationCode() != null) {
                    predicates.add(cb.equal(root.get("stationCode"), f.stationCode().trim().toUpperCase()));
                }
                if (f.comparisonGroup() != null) {
                    predicates.add(cb.equal(root.get("comparisonGroup"), f.comparisonGroup().trim().toUpperCase()));
                }
                if (f.active() != null) {
                    predicates.add(cb.equal(root.get("active"), f.active()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String upper(String value) {
        String v = blankToNull(value);
        return v == null ? null : v.toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
