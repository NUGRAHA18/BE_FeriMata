package com.fertimata.backend.ai;

import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.cultivation.dto.CycleStatusResponse;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menyusun input fuzzy layanan AI dari bacaan sensor terbaru.
 *
 * <p>Cek data basi ada di sini, bukan di tiap klien: field yang bacaannya tidak ada atau lebih tua dari
 * {@code app.ai.inputs.max-age} bernilai null dan membuat form-nya tidak siap. Layanan AI cukup memeriksa
 * {@code readyFor…} sebelum menjalankan pompa (S9). Backend tidak menolak perintah karena ini; keputusan
 * dosing tetap milik layanan AI.
 */
@Service
public class AiInputsService {

    private static final String HST = "hst";

    private final SensorService sensorService;
    private final CultivationService cultivationService;
    private final AiInputsProperties properties;
    private final Clock clock;

    public AiInputsService(
            SensorService sensorService,
            CultivationService cultivationService,
            AiInputsProperties properties,
            Clock clock) {
        this.sensorService = sensorService;
        this.cultivationService = cultivationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AiInputsResponse inputs() {
        Instant now = clock.instant();
        Map<String, SensorReadingResponse> latest = sensorService.latestPerSensor().stream()
                .collect(Collectors.toMap(SensorReadingResponse::metricKey, Function.identity(), (a, b) -> a));
        Optional<CycleStatusResponse> cultivation = cultivationService.activeStatus();
        Integer hst = cultivation.map(c -> (int) c.hst()).orElse(null);

        Map<String, AiInputsResponse.Reading> readings = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        Map<String, BigDecimal> fertilization =
                form("fertilization", properties.fertilization(), latest, readings, problems, now, hst);
        Map<String, BigDecimal> irrigation =
                form("irrigation", properties.irrigation(), latest, readings, problems, now, hst);

        return new AiInputsResponse(
                now,
                properties.maxAge().toSeconds(),
                hst,
                cultivation.map(c -> c.currentPhase() == null ? null : c.currentPhase().code()).orElse(null),
                cultivation.map(c -> c.currentPhase() == null ? null : c.currentPhase().name()).orElse(null),
                !fertilization.containsValue(null),
                !irrigation.containsValue(null),
                fertilization,
                irrigation,
                readings,
                problems);
    }

    private Map<String, BigDecimal> form(
            String formName,
            Map<String, String> fields,
            Map<String, SensorReadingResponse> latest,
            Map<String, AiInputsResponse.Reading> readings,
            List<String> problems,
            Instant now,
            Integer hst) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        fields.forEach((field, metricKey) -> {
            SensorReadingResponse reading = latest.get(metricKey);
            if (reading == null || reading.value() == null) {
                values.put(field, null);
                problems.add("%s.%s (%s): no reading".formatted(formName, field, metricKey));
                return;
            }
            AiInputsResponse.Reading entry = readings.computeIfAbsent(metricKey, k -> toReading(reading, now));
            if (!entry.fresh()) {
                values.put(field, null);
                problems.add("%s.%s (%s): stale, %d s old".formatted(formName, field, metricKey, entry.ageSeconds()));
                return;
            }
            values.put(field, entry.value());
        });
        values.put(HST, hst == null ? null : BigDecimal.valueOf(hst));
        if (hst == null) {
            problems.add("%s.hst: sowing date is not set".formatted(formName));
        }
        return values;
    }

    private AiInputsResponse.Reading toReading(SensorReadingResponse reading, Instant now) {
        long age = Math.max(0, Duration.between(reading.recordedAt(), now).toSeconds());
        boolean calibrated = reading.calibratedValue() != null;
        return new AiInputsResponse.Reading(
                calibrated ? reading.calibratedValue() : reading.value(),
                reading.value(),
                calibrated,
                reading.unit(),
                reading.recordedAt(),
                age,
                age <= properties.maxAge().toSeconds());
    }
}
