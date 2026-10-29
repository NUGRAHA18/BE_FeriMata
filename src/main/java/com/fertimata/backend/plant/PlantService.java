package com.fertimata.backend.plant;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.plant.dto.PlantRequest;
import com.fertimata.backend.plant.dto.PlantResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tanaman yang ditambahkan petani supaya tampil di dashboard: tambah, lihat, hapus. */
@Service
public class PlantService {

    private static final Logger log = LoggerFactory.getLogger(PlantService.class);

    private final PlantRepository plantRepository;
    private final CultivationService cultivationService;

    public PlantService(PlantRepository plantRepository, CultivationService cultivationService) {
        this.plantRepository = plantRepository;
        this.cultivationService = cultivationService;
    }

    /** @param active null untuk semua tanaman */
    @Transactional(readOnly = true)
    public List<PlantResponse> list(Boolean active) {
        List<Plant> plants = active == null
                ? plantRepository.findAllByOrderByNameAsc()
                : plantRepository.findByActiveOrderByNameAsc(active);
        return plants.stream().map(PlantResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlantResponse get(Long id) {
        return PlantResponse.from(require(id));
    }

    private Plant require(Long id) {
        return plantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plant", id));
    }

    @Transactional
    public PlantResponse create(PlantRequest request) {
        String name = blankToNull(request.name());
        String code = blankToNull(request.code());
        if (code == null && name == null) {
            throw new BusinessRuleException("A plant needs a name", Map.of("name", "required"));
        }
        if (code == null) {
            // Petani mengetik nama bebas ("Melon 06"); kode unik dibuat dari situ.
            code = uniqueCodeFrom(name);
        } else if (plantRepository.existsByCode(code)) {
            throw new BusinessRuleException(
                    "A plant with code %s already exists".formatted(code), Map.of("code", code));
        }
        Plant plant = new Plant(code, name == null ? code : name);
        plant.setDescription(blankToNull(request.description()));
        Plant saved = plantRepository.save(plant);
        log.info("Plant {} added", saved.getCode());
        return PlantResponse.from(saved);
    }

    /**
     * Menghapus tanaman. Deteksi AI yang menunjuknya tetap ada tanpa tanaman. Semua tanaman ditanam
     * bersamaan, jadi saat tanaman terakhir dihapus tanggal tanam ikut dikosongkan (keputusan 29 Okt 2026).
     */
    @Transactional
    public void delete(Long id) {
        Plant plant = require(id);
        int detached = plantRepository.detachDetections(id);
        plantRepository.delete(plant);
        plantRepository.flush();
        log.info("Plant {} deleted ({} AI detection(s) detached)", plant.getCode(), detached);
        if (plantRepository.count() == 0) {
            cultivationService.resetTransplantDate();
        }
    }

    /** "Melon 06" -> "Melon-06"; bila sudah dipakai, "Melon-06-2", "Melon-06-3", dst. */
    String uniqueCodeFrom(String name) {
        String base = name.trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (base.isEmpty()) {
            base = "Tanaman";
        }
        base = base.length() > 56 ? base.substring(0, 56) : base;
        String candidate = base;
        for (int n = 2; plantRepository.existsByCode(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
