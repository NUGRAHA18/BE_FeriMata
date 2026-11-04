package com.fertimata.backend.irrigation;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.common.exception.ResourceNotFoundException;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.irrigation.dto.IrrigationScheduleRequest;
import com.fertimata.backend.irrigation.dto.IrrigationScheduleResponse;
import com.fertimata.backend.irrigation.dto.IrrigationScheduleUpdateRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Jadwal siram harian (zona {@code app.cultivation.zone}); lama dibatasi {@code maxRunSeconds} pompa sejak disimpan. */
@Service
public class IrrigationScheduleService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationScheduleService.class);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** Data yang dibutuhkan runner, lepas dari entity. */
    public record Due(Long id, LocalTime time, int durationSeconds) {}

    private final IrrigationScheduleRepository repository;
    private final ActuatorRepository actuatorRepository;
    private final CultivationService cultivationService;
    private final IrrigationProperties properties;
    private final Clock clock;

    public IrrigationScheduleService(
            IrrigationScheduleRepository repository,
            ActuatorRepository actuatorRepository,
            CultivationService cultivationService,
            IrrigationProperties properties,
            Clock clock) {
        this.repository = repository;
        this.actuatorRepository = actuatorRepository;
        this.cultivationService = cultivationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IrrigationScheduleResponse> list() {
        return repository.findAllByOrderByTimeOfDayAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public IrrigationScheduleResponse create(IrrigationScheduleRequest request) {
        LocalTime time = minutes(request.time());
        requireFreeTime(time, null);
        requireDuration(request.durationSeconds());
        IrrigationSchedule schedule = new IrrigationSchedule();
        schedule.setTimeOfDay(time);
        schedule.setDurationSeconds(request.durationSeconds());
        schedule.setEnabled(request.enabled() == null || request.enabled());
        schedule.setNotes(blankToNull(request.notes()));
        startFromNextOccurrence(schedule);
        IrrigationSchedule saved = repository.save(schedule);
        log.info("Irrigation schedule {} added: {} s", HH_MM.format(time), saved.getDurationSeconds());
        return toResponse(saved);
    }

    @Transactional
    public IrrigationScheduleResponse update(Long id, IrrigationScheduleUpdateRequest request) {
        IrrigationSchedule schedule = require(id);
        if (request.time() != null) {
            LocalTime time = minutes(request.time());
            requireFreeTime(time, id);
            if (!time.equals(schedule.getTimeOfDay())) {
                schedule.setTimeOfDay(time);
                // Jam baru dihitung dari kemunculan berikutnya: jam yang sudah lewat hari ini mulai besok.
                startFromNextOccurrence(schedule);
            }
        }
        if (request.durationSeconds() != null) {
            requireDuration(request.durationSeconds());
            schedule.setDurationSeconds(request.durationSeconds());
        }
        if (request.enabled() != null) {
            schedule.setEnabled(request.enabled());
        }
        if (request.notes() != null) {
            schedule.setNotes(blankToNull(request.notes()));
        }
        return toResponse(schedule);
    }

    @Transactional
    public void delete(Long id) {
        IrrigationSchedule schedule = require(id);
        repository.delete(schedule);
        log.info("Irrigation schedule {} deleted", HH_MM.format(schedule.getTimeOfDay()));
    }

    /** Jadwal aktif yang jamnya sudah lewat hari ini dan belum berjalan hari ini. */
    @Transactional(readOnly = true)
    public List<Due> due(LocalDate today, LocalTime now) {
        return repository.findByEnabledTrueOrderByTimeOfDayAsc().stream()
                .filter(s -> !s.getTimeOfDay().isAfter(now))
                .filter(s -> s.getLastRunDate() == null || s.getLastRunDate().isBefore(today))
                .map(s -> new Due(s.getId(), s.getTimeOfDay(), s.getDurationSeconds()))
                .toList();
    }

    /** Satu jadwal aktif sebagai {@link Due}, untuk sesi yang menunggu pemupukan. */
    @Transactional(readOnly = true)
    public Optional<Due> find(Long id) {
        return repository.findById(id)
                .filter(IrrigationSchedule::isEnabled)
                .map(s -> new Due(s.getId(), s.getTimeOfDay(), s.getDurationSeconds()));
    }

    /** Menandai jadwal diambil hari ini sebelum perintah dikirim agar restart tidak menyiram dua kali; false bila tak berlaku. */
    @Transactional
    public boolean claim(Long id, LocalDate today, Instant at) {
        Optional<IrrigationSchedule> schedule = repository.findById(id)
                .filter(IrrigationSchedule::isEnabled)
                .filter(s -> s.getLastRunDate() == null || s.getLastRunDate().isBefore(today));
        schedule.ifPresent(s -> s.recordRun(today, at, IrrigationSchedule.RunResult.STARTED, null, null, null));
        return schedule.isPresent();
    }

    @Transactional
    public void record(
            Long id,
            LocalDate today,
            Instant at,
            IrrigationSchedule.RunResult result,
            String reasonCode,
            String message,
            String commandUid) {
        repository.findById(id).ifPresent(s -> s.recordRun(today, at, result, reasonCode, message, commandUid));
    }

    /** Seperti {@link #record}, ditambah durasi yang dipakai dan sumbernya (AI / FALLBACK / MANUAL). */
    @Transactional
    public void record(
            Long id,
            LocalDate today,
            Instant at,
            IrrigationSchedule.RunResult result,
            String reasonCode,
            String message,
            String commandUid,
            Integer durationSeconds) {
        repository.findById(id).ifPresent(s -> s.recordRun(
                today, at, result, reasonCode, message, commandUid, durationSeconds));
    }

    /** Pompa siram yang aktif, bila ada. */
    @Transactional(readOnly = true)
    public Optional<Actuator> pump() {
        return actuatorRepository.findFirstByCode(properties.actuatorCode()).filter(Actuator::isEnabled);
    }

    public ZoneId zone() {
        return cultivationService.zone();
    }

    private IrrigationSchedule require(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Irrigation schedule", id));
    }

    private void requireFreeTime(LocalTime time, Long ownId) {
        repository.findAllByOrderByTimeOfDayAsc().stream()
                .filter(s -> s.getTimeOfDay().equals(time) && !s.getId().equals(ownId))
                .findAny()
                .ifPresent(s -> {
                    throw new BusinessRuleException(
                            "There is already a schedule at %s".formatted(HH_MM.format(time)),
                            Map.of("time", HH_MM.format(time), "reason", "DUPLICATE_TIME"));
                });
    }

    private void requireDuration(int seconds) {
        Integer max = actuatorRepository.findFirstByCode(properties.actuatorCode())
                .map(Actuator::getMaxRunSeconds)
                .orElse(null);
        if (max != null && seconds > max) {
            throw new BusinessRuleException(
                    "The pump may run at most %d seconds".formatted(max),
                    Map.of("durationSeconds", seconds, "maxRunSeconds", max, "reason", "TOO_LONG"));
        }
    }

    private void startFromNextOccurrence(IrrigationSchedule schedule) {
        LocalDate today = LocalDate.now(clock.withZone(zone()));
        LocalTime now = LocalTime.now(clock.withZone(zone()));
        schedule.setLastRunDate(schedule.getTimeOfDay().isAfter(now) ? null : today);
    }

    private IrrigationScheduleResponse toResponse(IrrigationSchedule s) {
        return new IrrigationScheduleResponse(
                s.getId(),
                HH_MM.format(s.getTimeOfDay()),
                s.getDurationSeconds(),
                s.isEnabled(),
                s.getNotes(),
                nextRun(s),
                s.getLastRunAt(),
                s.getLastResult(),
                s.getLastReasonCode(),
                s.getLastMessage(),
                s.getLastCommandUid(),
                s.getLastDurationSeconds());
    }

    private Instant nextRun(IrrigationSchedule s) {
        if (!s.isEnabled() || !properties.enabled()) {
            return null;
        }
        LocalDate today = LocalDate.now(clock.withZone(zone()));
        LocalDate date = today.equals(s.getLastRunDate()) ? today.plusDays(1) : today;
        if (date.equals(today) && s.getTimeOfDay().isBefore(LocalTime.now(clock.withZone(zone())).minus(properties.grace()))) {
            date = today.plusDays(1);
        }
        return date.atTime(s.getTimeOfDay()).atZone(zone()).toInstant();
    }

    private static LocalTime minutes(LocalTime time) {
        return time.truncatedTo(ChronoUnit.MINUTES);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
