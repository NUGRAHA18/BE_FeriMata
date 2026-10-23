package com.fertimata.backend.research;

import com.fertimata.backend.plant.HarvestService;
import com.fertimata.backend.plant.PlantFilter;
import com.fertimata.backend.plant.dto.HarvestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/research")
@Tag(name = "Research", description = "Harvest data and CSV exports for the report (Protokol-Data.md §2, §6, §7)")
@SecurityRequirement(name = "bearerAuth")
public class ResearchController {

    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final HarvestService harvestService;
    private final ResearchExportService exportService;

    public ResearchController(HarvestService harvestService, ResearchExportService exportService) {
        this.harvestService = harvestService;
        this.exportService = exportService;
    }

    @GetMapping("/harvests")
    @Operation(summary = "Harvest records across plants", description = "Every filter is optional and they combine.")
    public List<HarvestResponse> harvests(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) String treatmentCode,
            @RequestParam(required = false) String blockCode,
            @RequestParam(required = false) String comparisonGroup) {
        return harvestService.search(new PlantFilter(cycleId, treatmentCode, blockCode, null, comparisonGroup, null));
    }

    @GetMapping("/exports/observations.csv")
    @Operation(summary = "Plant observations as CSV", description = "One row per observation; soil snapshot metrics become columns.")
    public ResponseEntity<byte[]> observationsCsv(@RequestParam(required = false) Long cycleId) {
        return csv("pengamatan", exportService.observations(cycleId));
    }

    @GetMapping("/exports/harvests.csv")
    @Operation(summary = "Harvest records as CSV")
    public ResponseEntity<byte[]> harvestsCsv(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) String treatmentCode,
            @RequestParam(required = false) String comparisonGroup) {
        return csv("panen", exportService.harvests(new PlantFilter(cycleId, treatmentCode, null, null, comparisonGroup, null)));
    }

    @GetMapping("/exports/calibration-samples.csv")
    @Operation(summary = "Paired lab samples of one sensor as CSV", description = "Covariates become columns.")
    public ResponseEntity<byte[]> calibrationSamplesCsv(@RequestParam Long sensorId) {
        return csv("sampel-kalibrasi-" + sensorId, exportService.calibrationSamples(sensorId));
    }

    private static ResponseEntity<byte[]> csv(String name, byte[] body) {
        String filename = "fertimata-%s-%s.csv".formatted(name, LocalDate.now());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
