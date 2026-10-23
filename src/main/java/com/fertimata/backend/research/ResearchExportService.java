package com.fertimata.backend.research;

import com.fertimata.backend.calibration.CalibrationSample;
import com.fertimata.backend.calibration.CalibrationSampleRepository;
import com.fertimata.backend.common.util.CsvWriter;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.plant.HarvestRecord;
import com.fertimata.backend.plant.HarvestService;
import com.fertimata.backend.plant.Plant;
import com.fertimata.backend.plant.PlantFilter;
import com.fertimata.backend.plant.PlantObservation;
import com.fertimata.backend.plant.PlantObservationRepository;
import com.fertimata.backend.sensor.SensorService;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ekspor CSV data penelitian untuk diolah di spreadsheet/R (BAB IV, Protokol-Data.md §2, §6, §7).
 *
 * <p>Waktu ditulis dalam zona greenhouse (WIB) tanpa offset supaya langsung terbaca di Excel.
 */
@Service
public class ResearchExportService {

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlantObservationRepository observationRepository;
    private final HarvestService harvestService;
    private final CalibrationSampleRepository sampleRepository;
    private final SensorService sensorService;
    private final CultivationService cultivationService;
    private final JsonSupport jsonSupport;

    public ResearchExportService(
            PlantObservationRepository observationRepository,
            HarvestService harvestService,
            CalibrationSampleRepository sampleRepository,
            SensorService sensorService,
            CultivationService cultivationService,
            JsonSupport jsonSupport) {
        this.observationRepository = observationRepository;
        this.harvestService = harvestService;
        this.sampleRepository = sampleRepository;
        this.sensorService = sensorService;
        this.cultivationService = cultivationService;
        this.jsonSupport = jsonSupport;
    }

    /** Satu baris per pengamatan; kolom tanah dibentuk dari semua metrik yang muncul di snapshot. */
    @Transactional(readOnly = true)
    public byte[] observations(Long cycleId) {
        List<PlantObservation> observations = observationRepository.findAllForExport(cycleId);
        List<Map<String, Object>> snapshots = observations.stream()
                .map(o -> jsonSupport.toMap(o.getSoilSnapshot()))
                .toList();
        TreeSet<String> soilKeys = new TreeSet<>();
        snapshots.forEach(s -> soilKeys.addAll(s.keySet()));

        List<Object> header = new ArrayList<>(plantHeader());
        header.addAll(Arrays.asList(
                "observed_at_wib", "hst", "height_cm", "leaf_count", "leaf3_length_cm", "leaf3_width_cm",
                "visual_notes", "observed_by"));
        header.addAll(soilKeys);
        CsvWriter csv = new CsvWriter().row(header);

        for (int i = 0; i < observations.size(); i++) {
            PlantObservation o = observations.get(i);
            List<Object> row = new ArrayList<>(plantColumns(o.getPlant()));
            row.addAll(Arrays.asList(
                    local(o.getObservedAt()),
                    o.getHst(),
                    o.getHeightCm(),
                    o.getLeafCount(),
                    o.getLeaf3LengthCm(),
                    o.getLeaf3WidthCm(),
                    o.getVisualNotes(),
                    o.getObservedBy() == null ? null : o.getObservedBy().getUsername()));
            Map<String, Object> snapshot = snapshots.get(i);
            for (String key : soilKeys) {
                row.add(snapshot.get(key) instanceof Map<?, ?> entry ? entry.get("value") : null);
            }
            csv.row(row);
        }
        return csv.toBytes();
    }

    @Transactional(readOnly = true)
    public byte[] harvests(PlantFilter filter) {
        List<Object> header = new ArrayList<>(plantHeader());
        header.addAll(Arrays.asList(
                "harvested_on", "hst", "fruit_weight_g", "brix", "firmness", "firmness_unit",
                "flesh_thickness_mm", "dry_matter_percent", "notes", "recorded_by"));
        CsvWriter csv = new CsvWriter().row(header);
        for (HarvestRecord h : harvestService.find(filter)) {
            List<Object> row = new ArrayList<>(plantColumns(h.getPlant()));
            row.addAll(Arrays.asList(
                    h.getHarvestedOn(),
                    h.getHst(),
                    h.getFruitWeightG(),
                    h.getBrix(),
                    h.getFirmness(),
                    h.getFirmnessUnit(),
                    h.getFleshThicknessMm(),
                    h.getDryMatterPercent(),
                    h.getNotes(),
                    h.getRecordedBy() == null ? null : h.getRecordedBy().getUsername()));
            csv.row(row);
        }
        return csv.toBytes();
    }

    /** Sampel lab berpasangan satu sensor, siap untuk regresi di luar backend. */
    @Transactional(readOnly = true)
    public byte[] calibrationSamples(Long sensorId) {
        sensorService.require(sensorId);
        List<CalibrationSample> samples = new ArrayList<>(sampleRepository.findBySensorIdOrderBySampledAtDesc(sensorId));
        samples.sort((a, b) -> a.getSampledAt().compareTo(b.getSampledAt()));
        List<Map<String, Object>> covariates = samples.stream()
                .map(s -> jsonSupport.toMap(s.getCovariates()))
                .toList();
        TreeSet<String> keys = new TreeSet<>();
        covariates.forEach(c -> keys.addAll(c.keySet()));

        List<Object> header = new ArrayList<>(Arrays.asList(
                "sensor_code", "metric_key", "sampled_at_wib", "sample_code", "sensor_value", "lab_value", "lab_method"));
        header.addAll(keys);
        header.add("notes");
        CsvWriter csv = new CsvWriter().row(header);
        for (int i = 0; i < samples.size(); i++) {
            CalibrationSample s = samples.get(i);
            List<Object> row = new ArrayList<>(Arrays.asList(
                    s.getSensor().getCode(),
                    s.getSensor().getMetricKey(),
                    local(s.getSampledAt()),
                    s.getSampleCode(),
                    s.getSensorValue(),
                    s.getLabValue(),
                    s.getLabMethod()));
            for (String key : keys) {
                row.add(covariates.get(i).get(key));
            }
            row.add(s.getNotes());
            csv.row(row);
        }
        return csv.toBytes();
    }

    private static List<Object> plantHeader() {
        return List.of("plant_code", "cycle_code", "treatment", "block", "row", "plot", "station", "comparison_group");
    }

    private static List<Object> plantColumns(Plant p) {
        return Arrays.asList(
                p.getCode(),
                p.getCycle() == null ? null : p.getCycle().getCode(),
                p.getTreatmentCode(),
                p.getBlockCode(),
                p.getRowCode(),
                p.getPlotCode(),
                p.getStationCode(),
                p.getComparisonGroup());
    }

    private String local(Instant instant) {
        return instant == null ? null : LOCAL_TIME.format(instant.atZone(cultivationService.zone()));
    }
}
