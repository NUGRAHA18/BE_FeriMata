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
 * Siram yang ditolak atau terlewat lebih dari {@code grace} tidak diulang; durasi dari {@link IrrigationDurationResolver}.
 */
@Component
public class IrrigationScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(IrrigationScheduleRunner.class);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    static final String PUMP_UNAVAILABLE = "PUMP_UNAVAILABLE";
    static final String BACKEND_DOWN = "BACKEND_DOWN";

    private final IrrigationScheduleService schedules;
    private final ActuatorCommandService commandService;
    private final AlertService alertService;
    private final IrrigationProperties properties;
    private final IrrigationDurationResolver durations;
    private final Clock clock;

    public IrrigationScheduleRunner(
            IrrigationScheduleService schedules,
            ActuatorCommandService commandService,
            AlertService alertService,
            IrrigationProperties properties,
            IrrigationDurationResolver durations,
            Clock clock) {
        this.schedules = schedules;
        this.commandService = commandService;
        this.alertService = alertService;
        this.properties = properties;
        this.durations = durations;
        this.clock = clock;
        log.info(
                "Scheduled irrigation {} (pump {}, checked every {}, duration from {})",
                properties.enabled() ? "active" : "switched off",
                properties.actuatorCode(),
                properties.checkInterval(),
                properties.durationSource());
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
            start(due, today, now);
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
        IrrigationDurationResolver.Decision duration =
                durations.resolve(due.durationSeconds(), pump.get().getMaxRunSeconds());
        if (duration.skip()) {
            schedules.record(due.id(), today, now, IrrigationSchedule.RunResult.SKIPPED, duration.reasonCode(),
                    "AI: tidak perlu siram (%s).".formatted(duration.detail()), null, 0, duration.source());
            log.info("Scheduled irrigation {} skipped: the AI fuzzy irrigation says no water is needed", at);
            return;
        }
        if (duration.source() == IrrigationDurationResolver.Source.FALLBACK) {
            warnFallback(due, at, duration);
        }
        try {
            ActuatorCommandResponse command = commandService.issueSystemCommand(
                    pump.get().getId(),
                    new ActuatorCommandRequest("ON", Map.of("durationSeconds", duration.seconds())));
            schedules.record(due.id(), today, now, IrrigationSchedule.RunResult.STARTED, duration.reasonCode(),
                    duration.detail(), command.commandUid(), duration.seconds(), duration.source());
            log.info("Scheduled irrigation {} started for {} s from {} ({})",
                    at, duration.seconds(), duration.source(), command.commandUid());
        } catch (SafetyInterlockException ex) {
            fail(due, today, now, IrrigationSchedule.RunResult.SKIPPED, reasonCode(ex), ex.getMessage(), ex);
        } catch (ApiException ex) {
            fail(due, today, now, IrrigationSchedule.RunResult.FAILED, reasonCode(ex), ex.getMessage(), ex);
        }
    }

    private void warnFallback(IrrigationScheduleService.Due due, String at, IrrigationDurationResolver.Decision duration) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scheduleId", due.id());
        metadata.put("time", at);
        metadata.put("reasonCode", duration.reasonCode());
        metadata.put("durationSeconds", duration.seconds());
        alertService.raise(NewAlert.builder(AlertType.IRRIGATION_AI_FALLBACK, AlertSeverity.INFO)
                .title("Irrigation used the fallback duration")
                .message("The AI fuzzy irrigation could not be used at %s (%s: %s); watering for the schedule's %d s instead"
                        .formatted(at, duration.reasonCode(), duration.detail(), duration.seconds()))
                .source(AlertSource.SYSTEM)
                .metadata(metadata)
                .build());
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
