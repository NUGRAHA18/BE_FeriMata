package com.fertimata.backend.metrics;

import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.actuator.domain.CommandStatus;
import com.fertimata.backend.calibration.PumpCalibrationService;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.device.DeviceRepository;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorRepository;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metrik teknis sistem (Protokol-Data.md §6): uptime, latensi sensor → backend, kelengkapan data
 * harian, siklus aktuasi, energi, dan volume pupuk.
 *
 * <p>Semua dihitung dari data yang sudah ada; tidak ada log tambahan. Konsekuensinya: rentang yang
 * lebih tua dari retensi pembacaan ({@code app.telemetry.retention.readings-max-age}) akan tampak
 * kosong, dan itu dicatat di {@code notes}.
 */
@Service
public class SystemMetricsService {

    private final SystemMetricsQueries queries;
    private final DeviceRepository deviceRepository;
    private final SensorRepository sensorRepository;
    private final ActuatorRepository actuatorRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final PumpCalibrationService pumpCalibrationService;
    private final CultivationService cultivationService;
    private final MetricsProperties properties;
    private final Clock clock;

    public SystemMetricsService(
            SystemMetricsQueries queries,
            DeviceRepository deviceRepository,
            SensorRepository sensorRepository,
            ActuatorRepository actuatorRepository,
            SensorReadingRepository sensorReadingRepository,
            PumpCalibrationService pumpCalibrationService,
            CultivationService cultivationService,
            MetricsProperties properties,
            Clock clock) {
        this.queries = queries;
        this.deviceRepository = deviceRepository;
        this.sensorRepository = sensorRepository;
        this.actuatorRepository = actuatorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.pumpCalibrationService = pumpCalibrationService;
        this.cultivationService = cultivationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemMetricsResponse metrics(Instant requestedFrom, Instant requestedTo) {
        Instant to = requestedTo == null ? clock.instant() : requestedTo;
        Instant from = requestedFrom == null ? to.minus(Duration.ofDays(7)) : requestedFrom;
        if (!from.isBefore(to)) {
            throw new BusinessRuleException("The start of the range must be before its end");
        }
        if (Duration.between(from, to).compareTo(properties.maxRange()) > 0) {
            throw new BusinessRuleException(
                    "The range may span at most %d days".formatted(properties.maxRange().toDays()),
                    Map.of("maxDays", properties.maxRange().toDays()));
        }
        List<String> notes = new ArrayList<>();
        return new SystemMetricsResponse(
                from,
                to,
                properties.expectedTelemetryInterval().toSeconds(),
                properties.uptimeBucket().toSeconds(),
                devices(from, to),
                actuation(from, to),
                energy(from, to, notes),
                pumpCalibrationService.dispensedByPumps(actuatorRepository.findAllWithDevice(), from, to),
                waterVolume(notes),
                notes);
    }

    // ── Telemetri per perangkat ───────────────────────────────────────────────

    private List<SystemMetricsResponse.DeviceMetrics> devices(Instant from, Instant to) {
        // Hanya perangkat yang punya sensor aktif: relay panel tidak mengirim telemetri, 0% di sana menyesatkan.
        Set<Long> reporting = new HashSet<>();
        for (Sensor sensor : sensorRepository.findAllWithDevice()) {
            if (sensor.isEnabled()) {
                reporting.add(sensor.getDevice().getId());
            }
        }
        long bucketSeconds = Math.max(1, properties.uptimeBucket().toSeconds());
        Map<Long, SystemMetricsQueries.DeviceTelemetryRow> rows = new HashMap<>();
        for (SystemMetricsQueries.DeviceTelemetryRow row : queries.telemetryPerDevice(from, to, bucketSeconds)) {
            rows.put(row.deviceId(), row);
        }
        ZoneId zone = cultivationService.zone();
        Map<Long, Map<LocalDate, Long>> daily = new HashMap<>();
        for (SystemMetricsQueries.DailyRow row : queries.messagesPerDay(from, to, zone)) {
            daily.computeIfAbsent(row.deviceId(), id -> new HashMap<>()).put(row.day(), row.messages());
        }

        long windowSeconds = Duration.between(from, to).toSeconds();
        long expected = expectedMessages(windowSeconds);
        long totalBuckets = Math.max(1, ceilDiv(windowSeconds, bucketSeconds));

        List<SystemMetricsResponse.DeviceMetrics> result = new ArrayList<>();
        for (Device device : deviceRepository.findAllByOrderByDeviceCodeAsc()) {
            if (!reporting.contains(device.getId()) && !rows.containsKey(device.getId())) {
                continue;
            }
            Optional<SystemMetricsQueries.DeviceTelemetryRow> row = Optional.ofNullable(rows.get(device.getId()));
            long received = row.map(SystemMetricsQueries.DeviceTelemetryRow::messages).orElse(0L);
            long live = row.map(SystemMetricsQueries.DeviceTelemetryRow::liveBuckets).orElse(0L);
            result.add(new SystemMetricsResponse.DeviceMetrics(
                    device.getId(),
                    device.getDeviceCode(),
                    device.getName(),
                    received,
                    expected,
                    percent(received, expected),
                    percent(Math.min(live, totalBuckets), totalBuckets),
                    row.map(r -> seconds(r.latencyP50())).orElse(null),
                    row.map(r -> seconds(r.latencyP95())).orElse(null),
                    row.map(r -> seconds(r.latencyMax())).orElse(null),
                    dailyCompleteness(daily.getOrDefault(device.getId(), Map.of()), from, to, zone)));
        }
        return result;
    }

    /** Satu entri per tanggal lokal di rentang; hari di tepi rentang hanya mengharapkan bagian yang tercakup. */
    private List<SystemMetricsResponse.DailyCompleteness> dailyCompleteness(
            Map<LocalDate, Long> counts, Instant from, Instant to, ZoneId zone) {
        List<SystemMetricsResponse.DailyCompleteness> days = new ArrayList<>();
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        while (!day.isAfter(last)) {
            Instant dayStart = day.atStartOfDay(zone).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
            Instant start = dayStart.isBefore(from) ? from : dayStart;
            Instant end = dayEnd.isAfter(to) ? to : dayEnd;
            long expected = expectedMessages(Duration.between(start, end).toSeconds());
            long received = counts.getOrDefault(day, 0L);
            days.add(new SystemMetricsResponse.DailyCompleteness(day, received, expected, percent(received, expected)));
            day = day.plusDays(1);
        }
        return days;
    }

    private long expectedMessages(long windowSeconds) {
        long interval = Math.max(1, properties.expectedTelemetryInterval().toSeconds());
        return windowSeconds / interval;
    }

    // ── Aktuasi ───────────────────────────────────────────────────────────────

    private SystemMetricsResponse.ActuationMetrics actuation(Instant from, Instant to) {
        Map<Long, long[]> perActuator = new LinkedHashMap<>();
        Map<Long, String[]> labels = new HashMap<>();
        for (SystemMetricsQueries.CommandRow row : queries.commandsPerActuator(from, to)) {
            long[] counts = perActuator.computeIfAbsent(row.actuatorId(), id -> new long[5]);
            labels.putIfAbsent(row.actuatorId(), new String[] {row.actuatorCode(), row.actuatorType()});
            counts[0] += row.count();
            switch (CommandStatus.valueOf(row.status())) {
                case EXECUTED -> counts[1] += row.count();
                case FAILED -> counts[2] += row.count();
                case PENDING, SENT -> counts[3] += row.count();
                case CANCELLED -> counts[4] += row.count();
            }
        }
        long[] totals = new long[5];
        List<SystemMetricsResponse.ActuatorActuation> rows = new ArrayList<>();
        perActuator.forEach((id, c) -> {
            for (int i = 0; i < totals.length; i++) {
                totals[i] += c[i];
            }
            String[] label = labels.get(id);
            rows.add(new SystemMetricsResponse.ActuatorActuation(id, label[0], label[1], c[0], c[1], c[2], c[3], c[4]));
        });
        return new SystemMetricsResponse.ActuationMetrics(
                totals[0], totals[1], totals[2], totals[3], totals[4], percent(totals[1], totals[1] + totals[2]), rows);
    }

    // ── Energi & air ──────────────────────────────────────────────────────────

    private SystemMetricsResponse.EnergyMetrics energy(Instant from, Instant to, List<String> notes) {
        String key = properties.energyMetricKey();
        Optional<SensorReading> first =
                sensorReadingRepository.findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtAsc(key, from, to);
        Optional<SensorReading> last =
                sensorReadingRepository.findFirstByMetricKeyAndRecordedAtBetweenOrderByRecordedAtDesc(key, from, to);
        BigDecimal firstValue = first.map(SensorReading::getNumericValue).orElse(null);
        BigDecimal lastValue = last.map(SensorReading::getNumericValue).orElse(null);
        BigDecimal consumed = null;
        if (firstValue != null && lastValue != null) {
            if (lastValue.compareTo(firstValue) >= 0) {
                consumed = lastValue.subtract(firstValue).setScale(3, RoundingMode.HALF_UP);
            } else {
                notes.add("The energy counter %s went down in the range (reset or replaced meter); consumption is not computed."
                        .formatted(key));
            }
        } else {
            notes.add("No %s readings in the range.".formatted(key));
        }
        return new SystemMetricsResponse.EnergyMetrics(
                key,
                consumed,
                firstValue,
                first.map(SensorReading::getRecordedAt).orElse(null),
                lastValue,
                last.map(SensorReading::getRecordedAt).orElse(null));
    }

    private BigDecimal waterVolume(List<String> notes) {
        // TODO(hardware): jumlahkan water.flow saat flow meter terpasang (rencana-pengembangan.md, pertanyaan #4).
        notes.add("Water volume needs a flow meter (water.flow), which is not installed yet.");
        return null;
    }

    // ── Pembantu ──────────────────────────────────────────────────────────────

    static BigDecimal percent(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal seconds(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
