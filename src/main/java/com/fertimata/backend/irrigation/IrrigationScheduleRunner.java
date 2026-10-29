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
 * Menjalankan jadwal siram: pada jamnya mengirim perintah ON berdurasi ke pompa siram.
 *
 * <p>Perintah lewat {@link ActuatorCommandService#issueSystemCommand}, jalur yang sama dengan tombol di
 * aplikasi, sehingga semua aturan keselamatan berlaku dan riwayat mencatat "SYSTEM". Siram yang ditolak
 * (PLN padam, air drum habis, pompa pupuk sedang jalan) tidak diulang; operator diberi peringatan.
 * Jadwal yang terlewat karena backend mati lebih lama dari {@code grace} juga tidak dikejar: siram siang
 * bolong untuk jadwal pagi bisa lebih merugikan daripada melewatkannya.
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
    private final Clock clock;

    public IrrigationScheduleRunner(
            IrrigationScheduleService schedules,
            ActuatorCommandService commandService,
            AlertService alertService,
            IrrigationProperties properties,
            Clock clock) {
        this.schedules = schedules;
        this.commandService = commandService;
        this.alertService = alertService;
        this.properties = properties;
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
        try {
            ActuatorCommandResponse command = commandService.issueSystemCommand(
                    pump.get().getId(),
                    new ActuatorCommandRequest("ON", Map.of("durationSeconds", due.durationSeconds())));
            schedules.record(due.id(), today, now, IrrigationSchedule.RunResult.STARTED, null, null, command.commandUid());
            log.info("Scheduled irrigation {} started for {} s ({})", at, due.durationSeconds(), command.commandUid());
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
