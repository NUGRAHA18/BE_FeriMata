package com.fertimata.backend.calibration;

import com.fertimata.backend.calibration.dto.CalibrationFitRequest;
import com.fertimata.backend.calibration.dto.CalibrationSampleRequest;
import com.fertimata.backend.calibration.dto.CalibrationSampleResponse;
import com.fertimata.backend.calibration.dto.DriftCheckRequest;
import com.fertimata.backend.calibration.dto.DriftCheckResponse;
import com.fertimata.backend.calibration.dto.SensorCalibrationRequest;
import com.fertimata.backend.calibration.dto.SensorCalibrationResponse;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorService;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.user.User;
import com.fertimata.backend.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kalibrasi sensor terhadap lab, sampel berpasangan, dan uji drift (Protokol-Data.md §7).
 *
 * <p>Backend tidak menilai apakah sebuah model "cukup baik"; ia menghitung dan menyimpan R², RMSE, MAE
 * dan Bland-Altman supaya tim bisa melaporkannya. Nilai mentah sensor tidak pernah ditulis ulang.
 */
@Service
public class SensorCalibrationService {

    private static final Logger log = LoggerFactory.getLogger(SensorCalibrationService.class);

    private final SensorService sensorService;
    private final SensorCalibrationRepository calibrationRepository;
    private final CalibrationSampleRepository sampleRepository;
    private final SensorDriftCheckRepository driftCheckRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final UserRepository userRepository;
    private final CalibrationProperties properties;
    private final JsonSupport jsonSupport;
    private final Clock clock;

    public SensorCalibrationService(
            SensorService sensorService,
            SensorCalibrationRepository calibrationRepository,
            CalibrationSampleRepository sampleRepository,
            SensorDriftCheckRepository driftCheckRepository,
            SensorReadingRepository sensorReadingRepository,
            UserRepository userRepository,
            CalibrationProperties properties,
            JsonSupport jsonSupport,
            Clock clock) {
        this.sensorService = sensorService;
        this.calibrationRepository = calibrationRepository;
        this.sampleRepository = sampleRepository;
        this.driftCheckRepository = driftCheckRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
        this.clock = clock;
    }

    // ── Model kalibrasi ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SensorCalibrationResponse> calibrations(Long sensorId) {
        sensorService.require(sensorId);
        List<SensorCalibration> all = calibrationRepository.findBySensorIdOrderByValidFromDesc(sensorId);
        SensorCalibration current = ReadingCalibrator.validAt(all, clock.instant());
        return all.stream().map(c -> toResponse(c, c == current)).toList();
    }

    /** Model yang dihitung di luar dan dimasukkan langsung. */
    @Transactional
    public SensorCalibrationResponse create(Long sensorId, SensorCalibrationRequest request, String username) {
        Sensor sensor = sensorService.require(sensorId);
        Map<String, Double> coefficients = new LinkedHashMap<>();
        if (request.covariates() != null) {
            request.covariates().forEach((key, value) -> coefficients.put(key.trim(), value.doubleValue()));
        }
        CalibrationModel model =
                new CalibrationModel(request.intercept().doubleValue(), request.slope().doubleValue(), coefficients);

        SensorCalibration calibration = newCalibration(sensor, model, request.validFrom(), request.notes(), username);
        calibration.setSampleCount(request.sampleCount());
        calibration.setRSquared(request.rSquared());
        calibration.setRmse(request.rmse());
        calibration.setMae(request.mae());
        // Statistik yang tidak dikirim dihitung dari sampel yang tersimpan, bila ada.
        List<RegressionFitter.Point> points = points(sampleRepository.findBySensorIdOrderBySampledAtDesc(sensorId));
        if (!points.isEmpty()) {
            RegressionFitter.Fit stats = RegressionFitter.evaluate(model, points);
            if (stats.sampleCount() > 0) {
                if (calibration.getSampleCount() == null) {
                    calibration.setSampleCount(stats.sampleCount());
                }
                if (calibration.getRSquared() == null) {
                    calibration.setRSquared(decimal(stats.rSquared(), 6));
                }
                if (calibration.getRmse() == null) {
                    calibration.setRmse(decimal(stats.rmse(), 6));
                }
                if (calibration.getMae() == null) {
                    calibration.setMae(decimal(stats.mae(), 6));
                }
                applyAgreement(calibration, stats);
            }
        }
        return save(calibration);
    }

    /** Kuadrat terkecil dari sampel lab; disimpan hanya bila {@code save = true}. */
    @Transactional
    public SensorCalibrationResponse fit(Long sensorId, CalibrationFitRequest request, String username) {
        Sensor sensor = sensorService.require(sensorId);
        Instant from = request.samplesFrom() == null ? Instant.EPOCH : request.samplesFrom();
        Instant to = request.samplesTo() == null ? clock.instant().plusSeconds(1) : request.samplesTo();
        List<CalibrationSample> samples =
                sampleRepository.findBySensorIdAndSampledAtBetweenOrderBySampledAtAsc(sensorId, from, to);
        List<String> keys = request.covariateKeys() == null
                ? List.of()
                : request.covariateKeys().stream().map(String::trim).filter(k -> !k.isEmpty()).distinct().toList();

        RegressionFitter.Fit fit = RegressionFitter.fit(points(samples), keys);
        SensorCalibration calibration = newCalibration(sensor, fit.model(), request.validFrom(), request.notes(), username);
        calibration.setSampleCount(fit.sampleCount());
        calibration.setRSquared(decimal(fit.rSquared(), 6));
        calibration.setRmse(decimal(fit.rmse(), 6));
        calibration.setMae(decimal(fit.mae(), 6));
        applyAgreement(calibration, fit);

        if (request.save() == null || !request.save()) {
            return toResponse(calibration, false);
        }
        return save(calibration);
    }

    private SensorCalibration newCalibration(
            Sensor sensor, CalibrationModel model, Instant validFrom, String notes, String username) {
        SensorCalibration calibration = new SensorCalibration();
        calibration.setSensor(sensor);
        calibration.setMethod(model.needsCovariates() ? CalibrationMethod.REGRESSION : CalibrationMethod.LINEAR);
        calibration.setIntercept(coefficient(model.intercept()));
        calibration.setSlope(coefficient(model.slope()));
        if (model.needsCovariates()) {
            Map<String, BigDecimal> coefficients = new LinkedHashMap<>();
            model.covariates().forEach((key, value) -> coefficients.put(key, coefficient(value)));
            calibration.setCovariates(jsonSupport.toJson(coefficients));
        }
        calibration.setValidFrom(validFrom == null ? clock.instant() : validFrom);
        calibration.setNotes(blankToNull(notes));
        calibration.setCreatedBy(user(username));
        return calibration;
    }

    private SensorCalibrationResponse save(SensorCalibration calibration) {
        SensorCalibration saved = calibrationRepository.save(calibration);
        log.info(
                "Calibration {} for sensor {} saved ({}, valid from {}, R2 {})",
                saved.getId(),
                saved.getSensor().getCode(),
                saved.getMethod(),
                saved.getValidFrom(),
                saved.getRSquared());
        boolean current = !saved.getValidFrom().isAfter(clock.instant())
                && ReadingCalibrator.validAt(
                                calibrationRepository.findBySensorIdOrderByValidFromDesc(saved.getSensor().getId()),
                                clock.instant())
                        == saved;
        return toResponse(saved, current);
    }

    private static void applyAgreement(SensorCalibration calibration, RegressionFitter.Fit stats) {
        calibration.setBias(decimal(stats.bias(), 6));
        calibration.setLoaLower(decimal(stats.loaLower(), 6));
        calibration.setLoaUpper(decimal(stats.loaUpper(), 6));
    }

    // ── Sampel lab ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CalibrationSampleResponse> samples(Long sensorId) {
        sensorService.require(sensorId);
        return sampleRepository.findBySensorIdOrderBySampledAtDesc(sensorId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CalibrationSampleResponse addSample(Long sensorId, CalibrationSampleRequest request, String username) {
        Sensor sensor = sensorService.require(sensorId);
        Instant sampledAt = request.sampledAt() == null ? clock.instant() : request.sampledAt();

        CalibrationSample sample = new CalibrationSample();
        sample.setSensor(sensor);
        sample.setSampledAt(sampledAt);
        sample.setLabValue(request.labValue());
        sample.setLabMethod(blankToNull(request.labMethod()));
        sample.setSampleCode(blankToNull(request.sampleCode()));
        sample.setNotes(blankToNull(request.notes()));
        sample.setRecordedBy(user(username));

        Optional<SensorReading> reading = request.sensorValue() != null && request.covariates() != null
                ? Optional.empty()
                : recentReading(sensor, sampledAt);
        if (request.sensorValue() != null) {
            sample.setSensorValue(request.sensorValue());
        } else {
            sample.setSensorValue(reading.map(SensorReading::getNumericValue).orElseThrow(() -> noRecentReading(sensor, "sensorValue")));
        }
        if (request.covariates() != null) {
            sample.setCovariates(request.covariates().isEmpty() ? null : jsonSupport.toJson(request.covariates()));
        } else {
            reading.map(this::sameMessageValues)
                    .filter(values -> !values.isEmpty())
                    .ifPresent(values -> sample.setCovariates(jsonSupport.toJson(values)));
        }
        CalibrationSample saved = sampleRepository.save(sample);
        log.info("Lab sample recorded for sensor {} (sensor {}, lab {})", sensor.getCode(), saved.getSensorValue(), saved.getLabValue());
        return toResponse(saved);
    }

    @Transactional
    public void deleteSample(Long sensorId, Long sampleId) {
        CalibrationSample sample = sampleRepository
                .findById(sampleId)
                .filter(s -> s.getSensor().getId().equals(sensorId))
                .orElseThrow(() -> new ResourceNotFoundException("CalibrationSample", sampleId));
        sampleRepository.delete(sample);
        log.info("Lab sample {} of sensor id={} deleted", sampleId, sensorId);
    }

    // ── Uji drift ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<DriftCheckResponse> driftChecks(Long sensorId, Pageable pageable) {
        sensorService.require(sensorId);
        return PageResponse.from(
                driftCheckRepository.findBySensorIdOrderByCheckedAtDesc(sensorId, pageable), DriftCheckResponse::from);
    }

    @Transactional
    public DriftCheckResponse addDriftCheck(Long sensorId, DriftCheckRequest request, String username) {
        Sensor sensor = sensorService.require(sensorId);
        Instant checkedAt = request.checkedAt() == null ? clock.instant() : request.checkedAt();
        BigDecimal measured = request.measuredValue() != null
                ? request.measuredValue()
                : recentReading(sensor, checkedAt)
                        .map(SensorReading::getNumericValue)
                        .orElseThrow(() -> noRecentReading(sensor, "measuredValue"));

        SensorDriftCheck check = new SensorDriftCheck();
        check.setSensor(sensor);
        check.setCheckedAt(checkedAt);
        check.setStandardName(request.standardName().trim());
        check.setReferenceValue(request.referenceValue());
        check.setMeasuredValue(measured);
        BigDecimal deviation = measured.subtract(request.referenceValue());
        check.setDeviation(deviation);
        if (request.referenceValue().signum() != 0) {
            check.setDeviationPercent(deviation
                    .multiply(BigDecimal.valueOf(100))
                    .divide(request.referenceValue().abs(), 4, RoundingMode.HALF_UP));
        }
        check.setProbeCleaned(Boolean.TRUE.equals(request.probeCleaned()));
        check.setNotes(blankToNull(request.notes()));
        check.setPerformedBy(user(username));
        SensorDriftCheck saved = driftCheckRepository.save(check);
        log.info("Drift check for sensor {}: {} vs {} ({}%)", sensor.getCode(), measured, request.referenceValue(), saved.getDeviationPercent());
        return DriftCheckResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Optional<SensorDriftCheck> lastDriftCheck(Long sensorId) {
        return driftCheckRepository.findFirstBySensorIdOrderByCheckedAtDesc(sensorId);
    }

    // ── Pembantu ──────────────────────────────────────────────────────────────

    /** Pembacaan numerik terakhir sensor pada/sebelum {@code at}, bila tidak lebih tua dari batas. */
    private Optional<SensorReading> recentReading(Sensor sensor, Instant at) {
        return sensorReadingRepository
                .findFirstBySensorIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(sensor.getId(), at)
                .filter(r -> r.getNumericValue() != null)
                .filter(r -> !r.getRecordedAt().isBefore(at.minus(properties.readingMaxAge())));
    }

    /** Metrik lain dari pesan telemetri yang sama; kandidat kovariat. */
    private Map<String, BigDecimal> sameMessageValues(SensorReading reading) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (SensorReading r : sensorReadingRepository.findByDeviceIdAndRecordedAt(
                reading.getDevice().getId(), reading.getRecordedAt())) {
            if (r.getNumericValue() != null && !r.getSensor().getId().equals(reading.getSensor().getId())) {
                values.put(r.getMetricKey(), r.getNumericValue());
            }
        }
        return values;
    }

    private BusinessRuleException noRecentReading(Sensor sensor, String field) {
        return new BusinessRuleException(
                "Sensor %s has no reading in the %s before that time; enter %s by hand"
                        .formatted(sensor.getCode(), properties.readingMaxAge(), field),
                Map.of("field", field));
    }

    private List<RegressionFitter.Point> points(List<CalibrationSample> samples) {
        List<RegressionFitter.Point> points = new ArrayList<>();
        for (CalibrationSample s : samples) {
            Map<String, Double> covariates = new LinkedHashMap<>();
            jsonSupport.toMap(s.getCovariates()).forEach((key, value) -> {
                if (value instanceof Number n) {
                    covariates.put(key, n.doubleValue());
                }
            });
            points.add(new RegressionFitter.Point(s.getSensorValue().doubleValue(), s.getLabValue().doubleValue(), covariates));
        }
        return points;
    }

    private SensorCalibrationResponse toResponse(SensorCalibration calibration, boolean current) {
        return SensorCalibrationResponse.from(calibration, jsonSupport.toMap(calibration.getCovariates()), current);
    }

    private CalibrationSampleResponse toResponse(CalibrationSample sample) {
        return CalibrationSampleResponse.from(sample, jsonSupport.toMap(sample.getCovariates()));
    }

    private User user(String username) {
        return username == null ? null : userRepository.findByUsername(username).orElse(null);
    }

    /** Statistik untuk kolom NUMERIC(18, scale); nilai yang tidak muat disimpan kosong, bukan menggagalkan simpan. */
    static BigDecimal decimal(double value, int scale) {
        if (!Double.isFinite(value) || Math.abs(value) >= Math.pow(10, 18 - scale)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    /** Koefisien model wajib ada; yang tidak muat di kolom berarti sampelnya bermasalah. */
    private static BigDecimal coefficient(double value) {
        BigDecimal result = decimal(value, 8);
        if (result == null) {
            throw new BusinessRuleException("The model has an out-of-range coefficient (%s); check the samples".formatted(value));
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
