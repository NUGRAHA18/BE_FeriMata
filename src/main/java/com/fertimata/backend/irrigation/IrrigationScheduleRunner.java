package com.fertimata.backend.irrigation;

import com.fertimata.backend.actuator.ActuatorCommandService;
import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.dto.ActuatorCommandRequest;
import com.fertimata.backend.actuator.dto.ActuatorCommandResponse;
import com.fertimata.backend.actuator.safety.SafetyInterlockException;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.alert.AlertSource;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.fertilization.FertilizationCoordinator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mengirim ON berdurasi ke pompa siram lewat {@link ActuatorCommandService#issueSystemCommand} (aturan keselamatan berlaku).
 * Siram yang ditolak atau terlewat lebih dari {@code grace} tidak diulang. Lama siram = durasi jadwal isian petani
 * (keputusan 4 Nov 2026: durasi siram ditentukan backend, bukan fuzzy irigasi AI).
 */
@Component
public class IrrigationScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(IrrigationScheduleRunner.class);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    static final String PUMP_UNAVAILABLE = "PUMP_UNAVAILABLE";
    static final String BACKEND_DOWN = "BACKEND_DOWN";
    static final String FERTILIZING = "FERTILIZING";
    static final String FERTILIZATION_IN_PROGRESS = "FERTILIZATION_IN_PROGRESS";

    private final IrrigationScheduleService schedules;
    private final ActuatorCommandService commandService;
    private final AlertService alertService;
    private final IrrigationProperties properties;
    private final FertilizationCoordinator fertilization;
    private final Clock clock;

    public IrrigationScheduleRunner(
            IrrigationScheduleService schedules,
            ActuatorCommandService commandService,
            AlertService alertService,
            IrrigationProperties properties,
            FertilizationCoordinator fertilization,
            Clock clock) {
        this.schedules = schedules;
        this.commandService = commandService;
        this.alertService = alertService;
        this.properties = properties;
        this.fertilization = fertilization;
        this.clock = clock;
        log.info(
                "Scheduled irrigation {} (pump {}, checked every {})",
                properties.enabled() ? "active" : "switched off",
                properties.actuatorCode(),
                properties.checkInterval());
    }

    @Scheduled(
            fixedDelayString = "${app.irrigation.check-interval}",
            initialDelayString = "${app.irrigation.check-interval}")
    public void run() {
        if (properties.enabled()) {
            runDue(clock.instant());
        }
    }

    /** Dipisah dari {@link #run()} agar bisa diuji tanpa scheduler. */
    public void runDue(Instant now) {
        ZonedDateTime local = now.atZone(schedules.zone());
        LocalDate today = local.toLocalDate();
        LocalTime time = local.toLocalTime();
        for (IrrigationScheduleService.Due due : schedules.due(today, time)) {
            if (!schedules.claim(due.id(), today, now)) {
                continue;
            }
            Duration late = Duration.between(due.time(), time);
            if (late.compareTo(properties.grace()) > 0) {
                String message = "Jadwal %s terlewat karena sistem tidak berjalan saat itu; siram tidak dikejar."
                        .formatted(HH_MM.format(due.time()));
                schedules.record(due.id(), today, now, IrrigationSchedule.RunResult.MISSED, BACKEND_DOWN, message, null);
                log.warn("Irrigation at {} missed by {}", due.time(), late);
                continue;
            }
            FertilizationCoordinator.Decision fertilizing = fertilizeFirst(due, today, now);
            if (fertilizing == FertilizationCoordinator.Decision.DEFERRED) {
                schedules.record(due.id(), today, now, IrrigationSchedule.RunResult.WAITING, FERTILIZING,
                        "Fertilizing first; watering starts after mixing.", null);
                log.info("Irrigation at {} waits for fertilization", HH_MM.format(due.time()));
                continue;
            }
            if (fertilizing == FertilizationCoordinator.Decision.BUSY) {
                schedules.record(due.id(), today, now, IrrigationSchedule.RunResult.SKIPPED, FERTILIZATION_IN_PROGRESS,
                        "Fertilization in progress; the session waiting for it waters afterwards.", null);
                log.info("Irrigation at {} skipped: fertilization in progress", HH_MM.format(due.time()));
                continue;
            }
            start(due, today, now);
        }
    }

    /** Sesi siram yang ditunda pemupukan: dijalankan setelah aduk selesai (tanpa batas grace jam jadwal). */
    public void startAfterFertilization(Long scheduleId, LocalDate date, Instant now) {
        Optional<IrrigationScheduleService.Due> due = schedules.find(scheduleId);
        if (due.isEmpty()) {
            log.info("Watering schedule {} waiting for fertilization was deleted or disabled", scheduleId);
            return;
        }
        start(due.get(), date, now);
    }

    /** Sesi siram yang ditunda pemupukan, tetapi backend mati terlalu lama: tidak dikejar. */
    public void missedAfterFertilization(Long scheduleId, LocalDate date, Instant now) {
        schedules.record(scheduleId, date, now, IrrigationSchedule.RunResult.MISSED, BACKEND_DOWN,
                "Backend was not running while fertilizing; watering was not chased.", null);
    }

    /** Pemupukan tidak boleh menggagalkan siram: error apa pun = siram seperti biasa. */
    private FertilizationCoordinator.Decision fertilizeFirst(IrrigationScheduleService.Due due, LocalDate today, Instant now) {
        try {
            return fertilization.beforeIrrigation(due.id(), today, now);
        } catch (RuntimeException ex) {
            log.error("Fertilization check before irrigation {} failed; watering as usual", HH_MM.format(due.time()), ex);
            return FertilizationCoordinator.Decision.PROCEED;
        }
    }

    private void start(IrrigationScheduleService.Due due, LocalDate today, Instant now) {
        String at = HH_MM.format(due.time());
        Optional<Actuator> pump = schedules.pump();
        if (pump.isEmpty()) {
            fail(due, today, now, IrrigationSchedule.RunResult.FAILED, PUMP_UNAVAILABLE,
                    "Pompa siram %s tidak ditemukan atau dimatikan.".formatted(properties.actuatorCode()), null);
            return;
        }
        int seconds = due.durationSeconds();
        try {
            ActuatorCommandResponse command = commandService.issueSystemCommand(
                    pump.get().getId(), new ActuatorCommandRequest("ON", Map.of("durationSeconds", seconds)));
            schedules.record(due.id(), today, now, IrrigationSchedule.RunResult.STARTED, null, null,
                    command.commandUid(), seconds);
            log.info("Scheduled irrigation {} started for {} s ({})", at, seconds, command.commandUid());
        } catch (SafetyInterlockException ex) {
            fail(due, today, now, IrrigationSchedule.RunResult.SKIPPED, reasonCode(ex), ex.getMessage(), ex);
        } catch (ApiException ex) {
            fail(due, today, now, IrrigationSchedule.RunResult.FAILED, reasonCode(ex), ex.getMessage(), ex);
        }
    }

    /** Kode interlock bila ada (mis. BACKUP_POWER, FLOAT_LOW), selain itu kode error API. */
    private static String reasonCode(ApiException ex) {
        Object interlock = ex.details().get("interlock");
        return interlock != null ? interlock.toString() : ex.errorCode().name();
    }

    private void fail(
            IrrigationScheduleService.Due due,
            LocalDate today,
            Instant now,
            IrrigationSchedule.RunResult result,
            String reasonCode,
            String reason,
            ApiException cause) {
        String at = HH_MM.format(due.time());
        schedules.record(due.id(), today, now, result, reasonCode, reason, null);
        log.warn("Scheduled irrigation {} {}: {}", at, result, reason);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scheduleId", due.id());
        metadata.put("time", at);
        metadata.put("result", result.name());
        metadata.put("reasonCode", reasonCode);
        if (cause != null) {
            metadata.put("errorCode", cause.errorCode().name());
            metadata.putAll(cause.details());
        }
        alertService.raise(NewAlert.builder(AlertType.IRRIGATION_SKIPPED, AlertSeverity.WARNING)
                .title("Scheduled irrigation skipped")
                .message("Irrigation scheduled at %s did not start: %s".formatted(at, reason))
                .source(AlertSource.SYSTEM)
                .metadata(metadata)
                .build());
    }
}
