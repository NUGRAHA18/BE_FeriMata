package com.fertimata.backend.calibration;

import com.fertimata.backend.actuator.ActuatorService;
import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorCommand;
import com.fertimata.backend.actuator.domain.ActuatorCommandRepository;
import com.fertimata.backend.actuator.domain.CommandStatus;
import com.fertimata.backend.actuator.safety.ActuatorSafetyProperties;
import com.fertimata.backend.calibration.dto.DispensedVolumeResponse;
import com.fertimata.backend.calibration.dto.PumpCalibrationRequest;
import com.fertimata.backend.calibration.dto.PumpCalibrationResponse;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.util.JsonSupport;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import com.fertimata.backend.user.UserRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Debit pompa peristaltik dan volume larutan yang dikeluarkan (Protokol-Data.md §6, §8).
 *
 * <p>Volume = durasi perintah yang di-ACK EXECUTED × debit kalibrasi terakhir sebelum perintah itu
 * dijalankan. Backend tidak tahu apakah pompa benar-benar memompa (selang bisa kosong); ini volume
 * nominal, sama seperti yang ditulis Protokol §6 "log durasi pompa × debit terkalibrasi".
 */
@Service
public class PumpCalibrationService {

    private static final Logger log = LoggerFactory.getLogger(PumpCalibrationService.class);

    /** Perintah setelah {@code to} masih dibaca sejauh ini, supaya run terakhir bisa dipotong oleh OFF-nya. */
    private static final Duration LOOKAHEAD = Duration.ofHours(6);

    private final ActuatorService actuatorService;
    private final ActuatorCalibrationRepository calibrationRepository;
    private final ActuatorCommandRepository commandRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final UserRepository userRepository;
    private final ActuatorSafetyProperties safetyProperties;
    private final CalibrationProperties properties;
    private final JsonSupport jsonSupport;
    private final Clock clock;

    public PumpCalibrationService(
            ActuatorService actuatorService,
            ActuatorCalibrationRepository calibrationRepository,
            ActuatorCommandRepository commandRepository,
            SensorReadingRepository sensorReadingRepository,
            UserRepository userRepository,
            ActuatorSafetyProperties safetyProperties,
            CalibrationProperties properties,
            JsonSupport jsonSupport,
            Clock clock) {
        this.actuatorService = actuatorService;
        this.calibrationRepository = calibrationRepository;
        this.commandRepository = commandRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.userRepository = userRepository;
        this.safetyProperties = safetyProperties;
        this.properties = properties;
        this.jsonSupport = jsonSupport;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PumpCalibrationResponse> calibrations(Long actuatorId) {
        actuatorService.require(actuatorId);
        List<ActuatorCalibration> all = calibrationRepository.findByActuatorIdOrderByCalibratedAtDesc(actuatorId);
        ActuatorCalibration current = validAt(all, clock.instant());
        return all.stream().map(c -> toResponse(c, c == current)).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ActuatorCalibration> currentCalibration(Long actuatorId) {
        return calibrationRepository.findFirstByActuatorIdOrderByCalibratedAtDesc(actuatorId);
    }

    @Transactional
    public PumpCalibrationResponse create(Long actuatorId, PumpCalibrationRequest request, String username) {
        Actuator actuator = actuatorService.require(actuatorId);
        if (!properties.pump().covers(actuator.getType())) {
            throw new BusinessRuleException(
                    "Actuator %s (%s) is not a calibrated pump; calibrated types are %s"
                            .formatted(actuator.getCode(), actuator.getType(), properties.pump().types()),
                    Map.of("type", String.valueOf(actuator.getType())));
        }
        Instant calibratedAt = request.calibratedAt() == null ? clock.instant() : request.calibratedAt();

        ActuatorCalibration calibration = new ActuatorCalibration();
        calibration.setActuator(actuator);
        calibration.setCalibratedAt(calibratedAt);
        List<PumpCalibrationRequest.Trial> trials = request.trials() == null ? List.of() : request.trials();
        if (!trials.isEmpty()) {
            if (trials.size() < properties.pump().minTrials()) {
                throw new BusinessRuleException(
                        "At least %d trials are required (Protokol §8); %d given"
                                .formatted(properties.pump().minTrials(), trials.size()),
                        Map.of("minTrials", properties.pump().minTrials()));
            }
            calibration.setFlowMlPerSecond(pooledFlow(trials));
            calibration.setTrialCount(trials.size());
            calibration.setFlowCvPercent(coefficientOfVariation(trials));
            List<Map<String, Object>> stored = new ArrayList<>();
            for (PumpCalibrationRequest.Trial t : trials) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("durationSeconds", t.durationSeconds());
                entry.put("volumeMl", t.volumeMl());
                stored.add(entry);
            }
            calibration.setTrials(jsonSupport.toJson(stored));
        } else if (request.flowMlPerSecond() != null) {
            calibration.setFlowMlPerSecond(request.flowMlPerSecond());
            calibration.setTrialCount(request.trialCount() == null ? 1 : request.trialCount());
        } else {
            throw new BusinessRuleException("Either trials or flowMlPerSecond is required");
        }
        calibration.setBusVoltage(request.busVoltage() != null ? request.busVoltage() : recentVoltage(calibratedAt));
        calibration.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        if (username != null) {
            calibration.setPerformedBy(userRepository.findByUsername(username).orElse(null));
        }

        ActuatorCalibration saved = calibrationRepository.save(calibration);
        log.info(
                "Pump {} calibrated: {} mL/s over {} trial(s), CV {}%",
                actuator.getCode(),
                saved.getFlowMlPerSecond(),
                saved.getTrialCount(),
                saved.getFlowCvPercent());
        ActuatorCalibration current = validAt(calibrationRepository.findByActuatorIdOrderByCalibratedAtDesc(actuatorId), clock.instant());
        return toResponse(saved, current == saved);
    }

    // ── Volume ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DispensedVolumeResponse dispensed(Long actuatorId, Instant from, Instant to) {
        Actuator actuator = actuatorService.require(actuatorId);
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(1)) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("The start of the range must not be after its end");
        }
        return dispensed(actuator, start, end);
    }

    /** Volume semua pompa terkalibrasi dalam rentang; dipakai metrik sistem. */
    @Transactional(readOnly = true)
    public List<DispensedVolumeResponse> dispensedByPumps(List<Actuator> actuators, Instant from, Instant to) {
        return actuators.stream()
                .filter(a -> properties.pump().covers(a.getType()))
                .map(a -> dispensed(a, from, to))
                .toList();
    }

    private DispensedVolumeResponse dispensed(Actuator actuator, Instant from, Instant to) {
        List<ActuatorCalibration> calibrations =
                calibrationRepository.findByActuatorIdOrderByCalibratedAtDesc(actuator.getId());
        List<ActuatorCommand> executed = commandRepository.findByActuatorIdAndStatusAndExecutedAtBetweenOrderByExecutedAtAsc(
                actuator.getId(), CommandStatus.EXECUTED, from, to.plus(LOOKAHEAD));

        int runs = 0;
        int uncalibrated = 0;
        int unknownDuration = 0;
        BigDecimal totalSeconds = BigDecimal.ZERO;
        BigDecimal volume = null;
        for (int i = 0; i < executed.size(); i++) {
            ActuatorCommand command = executed.get(i);
            if (command.getExecutedAt().isAfter(to)) {
                break;
            }
            if (!safetyProperties.isActivating(command.getCommandType())) {
                continue;
            }
            runs++;
            Optional<BigDecimal> requested = requestedSeconds(command);
            if (requested.isEmpty()) {
                unknownDuration++;
                continue;
            }
            BigDecimal seconds = requested.get();
            // Perintah berikutnya pada pompa yang sama (OFF, atau ON baru) mengakhiri run ini lebih awal.
            if (i + 1 < executed.size()) {
                Duration gap = Duration.between(command.getExecutedAt(), executed.get(i + 1).getExecutedAt());
                BigDecimal gapSeconds = BigDecimal.valueOf(gap.toMillis()).movePointLeft(3);
                seconds = seconds.min(gapSeconds);
            }
            totalSeconds = totalSeconds.add(seconds);
            ActuatorCalibration calibration = validAt(calibrations, command.getExecutedAt());
            if (calibration == null) {
                uncalibrated++;
                continue;
            }
            BigDecimal ml = seconds.multiply(calibration.getFlowMlPerSecond());
            volume = volume == null ? ml : volume.add(ml);
        }

        ActuatorCalibration current = calibrations.isEmpty() ? null : calibrations.get(0);
        return new DispensedVolumeResponse(
                actuator.getId(),
                actuator.getCode(),
                actuator.getName(),
                from,
                to,
                runs,
                totalSeconds.setScale(1, RoundingMode.HALF_UP),
                volume == null ? null : volume.setScale(1, RoundingMode.HALF_UP),
                uncalibrated,
                unknownDuration,
                current == null ? null : current.getFlowMlPerSecond(),
                current == null ? null : current.getCalibratedAt());
    }

    private Optional<BigDecimal> requestedSeconds(ActuatorCommand command) {
        Object value = jsonSupport.toMap(command.getParameters()).get(safetyProperties.durationParameter());
        if (value instanceof Number n) {
            return Optional.of(new BigDecimal(n.toString()));
        }
        if (value instanceof String s) {
            try {
                return Optional.of(new BigDecimal(s.trim()));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    // ── Pembantu ──────────────────────────────────────────────────────────────

    /** Daftar diurutkan menurun; yang pertama yang tidak lebih baru dari {@code at}. */
    static ActuatorCalibration validAt(List<ActuatorCalibration> calibrations, Instant at) {
        for (ActuatorCalibration c : calibrations) {
            if (!c.getCalibratedAt().isAfter(at)) {
                return c;
            }
        }
        return null;
    }

    /** Total volume / total durasi: ulangan yang lebih lama ikut berbobot lebih besar, seperti seharusnya. */
    static BigDecimal pooledFlow(List<PumpCalibrationRequest.Trial> trials) {
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal seconds = BigDecimal.ZERO;
        for (PumpCalibrationRequest.Trial t : trials) {
            volume = volume.add(t.volumeMl());
            seconds = seconds.add(t.durationSeconds());
        }
        return volume.divide(seconds, 6, RoundingMode.HALF_UP);
    }

    static BigDecimal coefficientOfVariation(List<PumpCalibrationRequest.Trial> trials) {
        if (trials.size() < 2) {
            return null;
        }
        double[] flows = trials.stream()
                .mapToDouble(t -> t.volumeMl().divide(t.durationSeconds(), MathContext.DECIMAL64).doubleValue())
                .toArray();
        double mean = java.util.Arrays.stream(flows).average().orElse(0);
        if (mean == 0) {
            return null;
        }
        double ss = 0;
        for (double f : flows) {
            ss += (f - mean) * (f - mean);
        }
        double sd = Math.sqrt(ss / (flows.length - 1));
        return BigDecimal.valueOf(sd / mean * 100).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal recentVoltage(Instant at) {
        return sensorReadingRepository
                .findFirstByMetricKeyAndRecordedAtLessThanEqualOrderByRecordedAtDesc(properties.voltageMetricKey(), at)
                .filter(r -> !r.getRecordedAt().isBefore(at.minus(properties.readingMaxAge())))
                .map(SensorReading::getNumericValue)
                .map(v -> v.setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private PumpCalibrationResponse toResponse(ActuatorCalibration calibration, boolean current) {
        return PumpCalibrationResponse.from(calibration, jsonSupport.toList(calibration.getTrials()), current);
    }
}
