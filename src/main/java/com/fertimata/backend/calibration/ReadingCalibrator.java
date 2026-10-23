package com.fertimata.backend.calibration;

import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Mengubah pembacaan menjadi respons dengan {@code calibratedValue}.
 *
 * <p>Model yang dipakai adalah kalibrasi terakhir yang {@code validFrom}-nya tidak lewat dari
 * {@code recordedAt} pembacaan, jadi riwayat lama tetap memakai model yang berlaku saat itu. Kovariat
 * (kelembapan, suhu tanah) diambil dari pesan telemetri yang sama - sensor 7-in-1 mengirim semuanya
 * bersamaan - lebih dulu dari daftar yang sedang dikonversi, baru ke database untuk yang kurang.
 * Pemanggil tanpa kalibrasi apa pun hanya membayar satu query kecil per halaman.
 */
@Component
public class ReadingCalibrator {

    private static final int SCALE = 4;

    private final SensorCalibrationRepository calibrationRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final JsonSupport jsonSupport;

    public ReadingCalibrator(
            SensorCalibrationRepository calibrationRepository,
            SensorReadingRepository sensorReadingRepository,
            JsonSupport jsonSupport) {
        this.calibrationRepository = calibrationRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.jsonSupport = jsonSupport;
    }

    public SensorReadingResponse toResponse(SensorReading reading) {
        return toResponses(List.of(reading)).get(0);
    }

    public List<SensorReadingResponse> toResponses(List<SensorReading> readings) {
        if (readings.isEmpty()) {
            return List.of();
        }
        Set<Long> sensorIds = new HashSet<>();
        for (SensorReading r : readings) {
            if (r.getNumericValue() != null) {
                sensorIds.add(r.getSensor().getId());
            }
        }
        Map<Long, List<SensorCalibration>> bySensor = new HashMap<>();
        if (!sensorIds.isEmpty()) {
            for (SensorCalibration c : calibrationRepository.findBySensorIdInOrderByValidFromDesc(sensorIds)) {
                bySensor.computeIfAbsent(c.getSensor().getId(), id -> new ArrayList<>()).add(c);
            }
        }
        if (bySensor.isEmpty()) {
            return readings.stream().map(SensorReadingResponse::from).toList();
        }

        // Identitas, bukan equals: BaseEntity menyamakan berdasarkan id, dan hashCode-nya konstan per kelas.
        Map<SensorReading, CalibrationModel> models = new IdentityHashMap<>();
        Map<Long, CalibrationModel> parsed = new HashMap<>();
        for (SensorReading r : readings) {
            if (r.getNumericValue() == null) {
                continue;
            }
            SensorCalibration calibration = validAt(bySensor.get(r.getSensor().getId()), r.getRecordedAt());
            if (calibration != null) {
                models.put(r, parsed.computeIfAbsent(calibration.getId(), id -> model(calibration)));
            }
        }

        Map<CovariateKey, Double> covariates = covariateValues(readings, models);
        return readings.stream()
                .map(r -> SensorReadingResponse.from(r, calibrated(r, models.get(r), covariates)))
                .toList();
    }

    public CalibrationModel model(SensorCalibration calibration) {
        Map<String, Double> coefficients = new LinkedHashMap<>();
        jsonSupport.toMap(calibration.getCovariates()).forEach((key, value) -> {
            if (value instanceof Number n) {
                coefficients.put(key, n.doubleValue());
            }
        });
        return new CalibrationModel(
                calibration.getIntercept().doubleValue(), calibration.getSlope().doubleValue(), coefficients);
    }

    /** Daftar diurutkan validFrom menurun, jadi yang pertama yang tidak lebih baru dari {@code at}. */
    static SensorCalibration validAt(List<SensorCalibration> calibrations, Instant at) {
        if (calibrations == null) {
            return null;
        }
        for (SensorCalibration c : calibrations) {
            if (!c.getValidFrom().isAfter(at)) {
                return c;
            }
        }
        return null;
    }

    private Map<CovariateKey, Double> covariateValues(
            List<SensorReading> readings, Map<SensorReading, CalibrationModel> models) {
        Map<CovariateKey, Double> values = new HashMap<>();
        for (SensorReading r : readings) {
            if (r.getNumericValue() != null) {
                values.put(CovariateKey.of(r), r.getNumericValue().doubleValue());
            }
        }
        // Yang belum ada di daftar: satu query per perangkat.
        Map<Long, Set<String>> missingKeys = new HashMap<>();
        Map<Long, Set<Instant>> missingTimes = new HashMap<>();
        models.forEach((r, model) -> {
            Long deviceId = r.getDevice().getId();
            for (String key : model.covariates().keySet()) {
                if (!values.containsKey(new CovariateKey(deviceId, key, r.getRecordedAt()))) {
                    missingKeys.computeIfAbsent(deviceId, id -> new HashSet<>()).add(key);
                    missingTimes.computeIfAbsent(deviceId, id -> new HashSet<>()).add(r.getRecordedAt());
                }
            }
        });
        missingKeys.forEach((deviceId, keys) -> {
            for (SensorReading r : sensorReadingRepository.findByDeviceIdAndMetricKeyInAndRecordedAtIn(
                    deviceId, keys, missingTimes.get(deviceId))) {
                if (r.getNumericValue() != null) {
                    values.putIfAbsent(CovariateKey.of(r), r.getNumericValue().doubleValue());
                }
            }
        });
        return values;
    }

    private static BigDecimal calibrated(
            SensorReading reading, CalibrationModel model, Map<CovariateKey, Double> covariates) {
        if (model == null) {
            return null;
        }
        Map<String, Double> values = new HashMap<>();
        for (String key : model.covariates().keySet()) {
            Double value = covariates.get(new CovariateKey(reading.getDevice().getId(), key, reading.getRecordedAt()));
            if (value != null) {
                values.put(key, value);
            }
        }
        OptionalDouble result = model.apply(reading.getNumericValue().doubleValue(), values);
        if (result.isEmpty() || !Double.isFinite(result.getAsDouble())) {
            return null;
        }
        return BigDecimal.valueOf(result.getAsDouble()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private record CovariateKey(Long deviceId, String metricKey, Instant recordedAt) {
        CovariateKey {
            Objects.requireNonNull(metricKey);
        }

        static CovariateKey of(SensorReading r) {
            return new CovariateKey(r.getDevice().getId(), r.getMetricKey(), r.getRecordedAt());
        }
    }
}
