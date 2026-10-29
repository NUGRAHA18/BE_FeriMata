package com.fertimata.backend.calibration;

import com.fertimata.backend.common.util.CsvWriter;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.sensor.SensorService;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sampel lab satu sensor sebagai CSV, untuk dibuka di Excel saat memeriksa hasil kalibrasi.
 *
 * <p>Waktu dalam zona kebun (WIB); setiap kovariat (mis. kelembapan tanah saat sampel diambil) menjadi
 * kolom sendiri.
 */
@Component
public class CalibrationSampleCsvExporter {

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CalibrationSampleRepository sampleRepository;
    private final SensorService sensorService;
    private final CultivationService cultivationService;
    private final JsonSupport jsonSupport;

    public CalibrationSampleCsvExporter(
            CalibrationSampleRepository sampleRepository,
            SensorService sensorService,
            CultivationService cultivationService,
            JsonSupport jsonSupport) {
        this.sampleRepository = sampleRepository;
        this.sensorService = sensorService;
        this.cultivationService = cultivationService;
        this.jsonSupport = jsonSupport;
    }

    @Transactional(readOnly = true)
    public byte[] export(Long sensorId) {
        sensorService.require(sensorId);
        List<CalibrationSample> samples = new ArrayList<>(sampleRepository.findBySensorIdOrderBySampledAtDesc(sensorId));
        samples.sort(Comparator.comparing(CalibrationSample::getSampledAt));
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

    private String local(Instant instant) {
        return instant == null ? null : LOCAL_TIME.format(instant.atZone(cultivationService.zone()));
    }
}
