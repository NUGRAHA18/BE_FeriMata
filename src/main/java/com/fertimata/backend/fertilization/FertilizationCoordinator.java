package com.fertimata.backend.fertilization;

import com.fertimata.backend.actuator.ActuatorCommandService;
import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.actuator.dto.ActuatorCommandRequest;
import com.fertimata.backend.actuator.dto.ActuatorCommandResponse;
import com.fertimata.backend.ai.AiInputsResponse;
import com.fertimata.backend.ai.AiInputsService;
import com.fertimata.backend.ai.AiServiceClient;
import com.fertimata.backend.ai.AiServiceException;
import com.fertimata.backend.ai.NoPhotoException;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.alert.AlertSource;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.common.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pemupukan otomatis mengikuti aturan main layanan AI (keputusan 2 Nov 2026):
 *
 * <ul>
 *   <li>jarak hari dari jadwal dosing AI; dosing pada sesi siram pertama yang jatuh tempo;
 *   <li>durasi N/P/K dari {@code run_fertilization}; tidak dosing dengan data EC/pH hilang atau basi (S9);
 *   <li>pompa aduk menyala dulu, pompa N, P, K bergantian, aduk {@code mix-seconds}, lalu sesi siram itu jalan.
 * </ul>
 *
 * <p>Semua perintah lewat jalur perintah biasa, jadi aturan keselamatan backend tetap berlaku. Langkah disimpan di
 * {@code fertilization_run}, bukan ditunggu dengan sleep, sehingga restart backend tidak mengulang dosing.
 */
@Component
public class FertilizationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(FertilizationCoordinator.class);

    static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    static final String AI_INPUTS_NOT_READY = "AI_INPUTS_NOT_READY";
    /** Tidak ada foto patroli segar; AI menolak tanpa foto, jadi tidak dipanggil. */
    static final String AI_NO_PHOTO = "AI_NO_PHOTO";
    static final String PUMP_UNAVAILABLE = "PUMP_UNAVAILABLE";
    static final String AI_NO_DOSE_NEEDED = "AI_NO_DOSE_NEEDED";
    static final String AI_CAPPED = "AI_CAPPED";
    static final String BACKEND_DOWN = "BACKEND_DOWN";

    /** Jawaban untuk sesi siram yang jatuh tempo. */
    public enum Decision {
        /** Tidak ada pemupukan sekarang; siram seperti biasa. */
        PROCEED,
        /** Pemupukan dimulai; sesi siram ini dijalankan {@link FertilizationRunner} setelah aduk selesai. */
        DEFERRED,
        /** Pemupukan lain sedang berjalan; pompa siram akan ditolak aturan keselamatan, jadi sesi ini dilewati. */
        BUSY
    }

    /** Sesi siram yang ditunggu pemupukan; {@code missed} = backend mati terlalu lama, siram tidak dikejar. */
    public record Irrigate(Long scheduleId, LocalDate date, boolean missed) {}

    private record Pumps(Actuator nitrogen, Actuator phosphorus, Actuator potassium, Actuator stirrer) {}

    private final FertilizationRunRepository repository;
    private final FertilizationService service;
    private final FertilizationProperties properties;
    private final AiServiceClient aiClient;
    private final AiInputsService aiInputs;
    private final ActuatorRepository actuatorRepository;
    private final ActuatorCommandService commandService;
    private final AlertService alertService;

    public FertilizationCoordinator(
            FertilizationRunRepository repository,
            FertilizationService service,
            FertilizationProperties properties,
            AiServiceClient aiClient,
            AiInputsService aiInputs,
            ActuatorRepository actuatorRepository,
            ActuatorCommandService commandService,
            AlertService alertService) {
        this.repository = repository;
        this.service = service;
        this.properties = properties;
        this.aiClient = aiClient;
        this.aiInputs = aiInputs;
        this.actuatorRepository = actuatorRepository;
        this.commandService = commandService;
        this.alertService = alertService;
    }

    /** Dipanggil runner siram tepat sebelum menyalakan pompa siram untuk satu sesi yang jatuh tempo. */
    public Decision beforeIrrigation(Long scheduleId, LocalDate today, Instant now) {
        if (!properties.enabled() || !aiClient.configured()) {
            return Decision.PROCEED;
        }
        if (repository.findFirstByStepIsNotNull().isPresent()) {
            return Decision.BUSY;
        }
        Optional<AiServiceClient.FertilizationInterval> row;
        try {
            row = FertilizationService.pick(aiClient.fertilizationIntervals());
        } catch (AiServiceException ex) {
            // Jatuh tempo tidak bisa diketahui tanpa jadwal AI; dicatat sekali sehari.
            notPoured(scheduleId, today, now, FertilizationRun.Status.FAILED, AI_UNAVAILABLE,
                    "Could not read the AI fertilization schedule: " + ex.getMessage(), null);
            return Decision.PROCEED;
        }
        if (row.isEmpty()) {
            return Decision.PROCEED;
        }
        int interval = row.get().intervalDays();
        if (!service.due(today, interval)) {
            return Decision.PROCEED;
        }
        AiInputsResponse inputs = aiInputs.inputs();
        if (!inputs.readyForFertilization()) {
            notPoured(scheduleId, today, now, FertilizationRun.Status.SKIPPED, AI_INPUTS_NOT_READY,
                    String.join("; ", inputs.problems()), interval);
            return Decision.PROCEED;
        }
        Optional<Pumps> pumps = pumps();
        if (pumps.isEmpty()) {
            notPoured(scheduleId, today, now, FertilizationRun.Status.SKIPPED, PUMP_UNAVAILABLE,
                    "Dosing pumps %s/%s/%s or stirrer %s not found or disabled".formatted(
                            properties.actuators().nitrogen(), properties.actuators().phosphorus(),
                            properties.actuators().potassium(), properties.actuators().stirrer()), interval);
            return Decision.PROCEED;
        }
        AiServiceClient.Dose dose;
        try {
            dose = aiClient.fertilizationDose(inputs.fertilization());
        } catch (NoPhotoException ex) {
            notPoured(scheduleId, today, now, FertilizationRun.Status.SKIPPED, AI_NO_PHOTO, ex.getMessage(), interval);
            return Decision.PROCEED;
        } catch (AiServiceException ex) {
            notPoured(scheduleId, today, now, FertilizationRun.Status.FAILED, AI_UNAVAILABLE, ex.getMessage(), interval);
            return Decision.PROCEED;
        }
        return start(scheduleId, today, now, interval, dose, pumps.get());
    }

    /**
     * Mengerjakan langkah yang sudah waktunya. Mengembalikan sesi siram yang harus dijalankan (atau dicatat terlewat)
     * bila pemupukan selesai.
     */
    public Optional<Irrigate> advance(Instant now) {
        Optional<FertilizationRun> active = repository.findFirstByStepIsNotNull();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        FertilizationRun run = active.get();
        if (run.getNextActionAt() != null && now.isBefore(run.getNextActionAt())) {
            return Optional.empty();
        }
        if (run.getNextActionAt() != null && Duration.between(run.getNextActionAt(), now).compareTo(properties.grace()) > 0) {
            return Optional.of(backendWasDown(run, now));
        }
        Optional<Pumps> pumps = pumps();
        // Langkah berdurasi 0 dilewati dalam putaran yang sama.
        for (int i = 0; i < FertilizationRun.Step.values().length && run.getStep() != null; i++) {
            if (run.getNextActionAt() != null && now.isBefore(run.getNextActionAt())) {
                break;
            }
            Gate gate = gate(run, now);
            if (gate.kind() == Gate.Kind.WAIT) {
                break;
            }
            if (gate.kind() == Gate.Kind.FAILED) {
                // Edge menolak perintah sebelumnya (ACK gagal) atau pompa sebelumnya tidak melapor OFF.
                run = abort(run, now, gate.reasonCode(), gate.message(), pumps.map(Pumps::stirrer).orElse(null));
                continue;
            }
            switch (run.getStep()) {
                case PHOSPHORUS -> {
                    if (pumps.isEmpty()) {
                        run = abort(run, now, PUMP_UNAVAILABLE, "Dosing pump disappeared during the run", null);
                    } else {
                        run = dose(run, now, pumps.get().phosphorus(), run.getPhosphorusSeconds(),
                                FertilizationRun.Step.POTASSIUM, pumps.get().stirrer());
                    }
                }
                case POTASSIUM -> {
                    if (pumps.isEmpty()) {
                        run = abort(run, now, PUMP_UNAVAILABLE, "Dosing pump disappeared during the run", null);
                    } else {
                        run = dose(run, now, pumps.get().potassium(), run.getPotassiumSeconds(),
                                FertilizationRun.Step.MIXING, pumps.get().stirrer());
                    }
                }
                case MIXING -> {
                    run.setStatus(FertilizationRun.Status.DONE);
                    run.setFinishedAt(now);
                    run.setStep(null);
                    run.setNextActionAt(null);
                    run = repository.save(run);
                    log.info("Fertilization {} done: N {} s, P {} s, K {} s", run.getId(), run.getNitrogenSeconds(),
                            run.getPhosphorusSeconds(), run.getPotassiumSeconds());
                    return Optional.of(new Irrigate(run.getIrrigationScheduleId(), run.getRunDate(), false));
                }
                case IRRIGATE -> {
                    run.setStep(null);
                    run.setNextActionAt(null);
                    if (run.getFinishedAt() == null) {
                        run.setFinishedAt(now);
                    }
                    run = repository.save(run);
                    return Optional.of(new Irrigate(run.getIrrigationScheduleId(), run.getRunDate(), false));
                }
            }
        }
        return Optional.empty();
    }

    /** Boleh lanjut ke langkah berikutnya? */
    record Gate(Kind kind, String reasonCode, String message) {
        enum Kind {
            READY,
            WAIT,
            FAILED
        }

        static final Gate READY = new Gate(Kind.READY, null, null);
        static final Gate WAIT = new Gate(Kind.WAIT, null, null);

        static Gate failed(String reasonCode, String message) {
            return new Gate(Kind.FAILED, reasonCode, message);
        }
    }

    /**
     * Edge hanya menyalakan satu pompa dosing pada satu waktu, dan menolak pompa siram selama aduk/dosing masih ON.
     * Penolakannya datang lewat ACK, setelah perintah terkirim. Karena itu langkah berikutnya menunggu: perintah
     * sebelumnya sudah di-ACK berhasil dan aktuatornya sudah melapor OFF. Lebih dari {@code step-timeout}: dosing
     * dihentikan, sedangkan siram tetap dicoba.
     */
    Gate gate(FertilizationRun run, Instant now) {
        boolean overdue = run.getNextActionAt() != null
                && Duration.between(run.getNextActionAt(), now).compareTo(properties.stepTimeout()) > 0;
        boolean dosing = run.getStatus() == FertilizationRun.Status.IN_PROGRESS;
        if (dosing) {
            Gate stirrer = ack(run.getStirrerCommandUid(), properties.actuators().stirrer(), overdue);
            if (stirrer.kind() == Gate.Kind.FAILED) {
                return stirrer;
            }
        }
        FertilizationRun.Step step = run.getStep();
        if (dosing && step != FertilizationRun.Step.IRRIGATE) {
            String previousUid;
            String previousCode;
            switch (step) {
                case PHOSPHORUS -> {
                    previousUid = run.getNitrogenCommandUid();
                    previousCode = properties.actuators().nitrogen();
                }
                case POTASSIUM -> {
                    previousUid = run.getPhosphorusCommandUid();
                    previousCode = properties.actuators().phosphorus();
                }
                default -> {
                    previousUid = run.getPotassiumCommandUid();
                    previousCode = properties.actuators().potassium();
                }
            }
            Gate previous = ack(previousUid, previousCode, overdue);
            if (previous.kind() == Gate.Kind.FAILED && isRefusal(previous)) {
                // Pompa itu tidak menuang: jangan dihitung sebagai sudah dosing.
                forget(run, step);
            }
            if (previous.kind() != Gate.Kind.READY) {
                return previous;
            }
            if (previousUid != null && active(previousCode)) {
                return overdue
                        ? Gate.failed("PREVIOUS_PUMP_STILL_ON", "%s still reports ON".formatted(previousCode))
                        : Gate.WAIT;
            }
        }
        if (step == FertilizationRun.Step.MIXING || step == FertilizationRun.Step.IRRIGATE) {
            // Siram menunggu pompa aduk OFF; setelah step-timeout dicoba saja, penolakannya dicatat runner siram.
            if (active(properties.actuators().stirrer()) && !overdue) {
                return Gate.WAIT;
            }
        }
        return Gate.READY;
    }

    /** Status ACK perintah {@code uid}; null = pompa itu tidak dinyalakan (0 detik). */
    private Gate ack(String uid, String actuatorCode, boolean overdue) {
        if (uid == null) {
            return Gate.READY;
        }
        ActuatorCommandResponse command;
        try {
            command = commandService.byUid(uid);
        } catch (ApiException ex) {
            return Gate.failed("COMMAND_NOT_FOUND", "Command %s of %s not found".formatted(uid, actuatorCode));
        }
        return switch (command.status()) {
            case EXECUTED -> Gate.READY;
            case FAILED, CANCELLED -> Gate.failed(
                    command.failureCode() != null ? command.failureCode() : "DEVICE_REFUSED",
                    "%s %s (command %s): %s".formatted(actuatorCode, command.status(), uid, command.errorMessage()));
            default -> overdue
                    ? Gate.failed("NO_ACK", "%s did not acknowledge command %s".formatted(actuatorCode, uid))
                    : Gate.WAIT;
        };
    }

    /** Penolakan yang pasti (pompa tidak menyala), bukan ACK yang hilang. */
    private static boolean isRefusal(Gate gate) {
        return !"NO_ACK".equals(gate.reasonCode());
    }

    private static void forget(FertilizationRun run, FertilizationRun.Step step) {
        switch (step) {
            case PHOSPHORUS -> run.setNitrogenCommandUid(null);
            case POTASSIUM -> run.setPhosphorusCommandUid(null);
            default -> run.setPotassiumCommandUid(null);
        }
    }

    private boolean active(String actuatorCode) {
        return actuatorRepository.findFirstByCode(actuatorCode)
                .map(Actuator::getCurrentState)
                .filter(state -> state.equalsIgnoreCase("ON"))
                .isPresent();
    }

    private Decision start(
            Long scheduleId, LocalDate today, Instant now, int interval, AiServiceClient.Dose dose, Pumps pumps) {
        FertilizationRun run = new FertilizationRun();
        run.setRunDate(today);
        run.setIrrigationScheduleId(scheduleId);
        run.setIntervalDays(interval);
        run.setStartedAt(now);
        run.setAiNitrogenSeconds(decimal(dose.nitrogenSeconds()));
        run.setAiPhosphorusSeconds(decimal(dose.phosphorusSeconds()));
        run.setAiPotassiumSeconds(decimal(dose.potassiumSeconds()));
        List<String> capped = new ArrayList<>();
        run.setNitrogenSeconds(seconds("N", dose.nitrogenSeconds(), pumps.nitrogen(), capped));
        run.setPhosphorusSeconds(seconds("P", dose.phosphorusSeconds(), pumps.phosphorus(), capped));
        run.setPotassiumSeconds(seconds("K", dose.potassiumSeconds(), pumps.potassium(), capped));
        if (!capped.isEmpty()) {
            run.setReasonCode(AI_CAPPED);
            run.setMessage("Capped to the pump limit: " + String.join(", ", capped));
        }
        int total = run.getNitrogenSeconds() + run.getPhosphorusSeconds() + run.getPotassiumSeconds();
        if (total == 0) {
            run.setStatus(FertilizationRun.Status.DONE);
            run.setReasonCode(AI_NO_DOSE_NEEDED);
            run.setMessage("AI fuzzy fertilization returned 0 s for N, P and K");
            run.setFinishedAt(now);
            repository.save(run);
            log.info("Fertilization due but the AI asked for no dose");
            return Decision.PROCEED;
        }
        run.setStirrerSeconds(stirrerSeconds(total, pumps.stirrer()));
        run.setStatus(FertilizationRun.Status.IN_PROGRESS);
        run = repository.save(run);

        try {
            run.setStirrerCommandUid(on(pumps.stirrer(), run.getStirrerSeconds()));
        } catch (ApiException ex) {
            run.setStatus(FertilizationRun.Status.FAILED);
            run.setReasonCode(reasonCode(ex));
            run.setMessage(ex.getMessage());
            run.setFinishedAt(now);
            run = repository.save(run);
            raise(run, AlertType.FERTILIZATION_SKIPPED, ex);
            return Decision.PROCEED;
        }
        run.setStep(FertilizationRun.Step.PHOSPHORUS);
        run.setNextActionAt(now);
        run = dose(run, now, pumps.nitrogen(), run.getNitrogenSeconds(), FertilizationRun.Step.PHOSPHORUS,
                pumps.stirrer());
        log.info("Fertilization {} started before watering schedule {}: N {} s, P {} s, K {} s, stirrer {} s",
                run.getId(), scheduleId, run.getNitrogenSeconds(), run.getPhosphorusSeconds(),
                run.getPotassiumSeconds(), run.getStirrerSeconds());
        return Decision.DEFERRED;
    }

    /** Menyalakan satu pompa dosing lalu menjadwalkan {@code next}; gagal = hentikan aduk dan siram setelah jeda. */
    private FertilizationRun dose(
            FertilizationRun run, Instant now, Actuator pump, Integer seconds, FertilizationRun.Step next,
            Actuator stirrer) {
        int s = seconds == null ? 0 : seconds;
        if (s > 0) {
            try {
                String uid = on(pump, s);
                switch (next) {
                    case PHOSPHORUS -> run.setNitrogenCommandUid(uid);
                    case POTASSIUM -> run.setPhosphorusCommandUid(uid);
                    default -> run.setPotassiumCommandUid(uid);
                }
            } catch (ApiException ex) {
                return abort(run, now, reasonCode(ex), ex.getMessage(), stirrer, ex);
            }
        }
        run.setStep(next);
        Instant pumpEnd = now.plusSeconds(s);
        if (next == FertilizationRun.Step.MIXING) {
            // Pompa aduk mati sendiri di akhir durasinya (sudah termasuk mix-seconds); siram setelah itu + jeda lapor OFF.
            Instant stirrerEnd = run.getStartedAt().plusSeconds(run.getStirrerSeconds());
            Instant end = stirrerEnd.isAfter(pumpEnd) ? stirrerEnd : pumpEnd;
            run.setNextActionAt(end.plus(properties.settle()));
        } else {
            run.setNextActionAt(s > 0 ? pumpEnd.plus(properties.pumpGap()) : now);
        }
        return repository.save(run);
    }

    private FertilizationRun abort(FertilizationRun run, Instant now, String reasonCode, String message, Actuator stirrer) {
        return abort(run, now, reasonCode, message, stirrer, null);
    }

    /** Dosing berhenti: aduk dimatikan, sesi siram tetap jalan setelah jeda. */
    private FertilizationRun abort(
            FertilizationRun run, Instant now, String reasonCode, String message, Actuator stirrer, ApiException cause) {
        boolean partial = run.anyDosed();
        run.setStatus(partial ? FertilizationRun.Status.PARTIAL : FertilizationRun.Status.FAILED);
        run.setReasonCode(reasonCode);
        run.setMessage(message);
        run.setFinishedAt(now);
        run.setStep(FertilizationRun.Step.IRRIGATE);
        run.setNextActionAt(now.plus(properties.settle()));
        if (stirrer != null) {
            try {
                commandService.issueSystemCommand(stirrer.getId(), new ActuatorCommandRequest("OFF", Map.of()));
            } catch (ApiException ex) {
                log.warn("Could not switch the stirrer off after a failed fertilization: {}", ex.getMessage());
            }
        }
        run = repository.save(run);
        raise(run, partial ? AlertType.FERTILIZATION_PARTIAL : AlertType.FERTILIZATION_SKIPPED, cause);
        return run;
    }

    private Irrigate backendWasDown(FertilizationRun run, Instant now) {
        boolean stillDosing = run.getStatus() == FertilizationRun.Status.IN_PROGRESS;
        if (stillDosing) {
            run.setStatus(run.anyDosed() ? FertilizationRun.Status.PARTIAL : FertilizationRun.Status.FAILED);
            run.setReasonCode(BACKEND_DOWN);
            run.setMessage("The backend was not running at step %s; fertilization was not finished".formatted(run.getStep()));
        }
        if (run.getFinishedAt() == null) {
            run.setFinishedAt(now);
        }
        run.setStep(null);
        run.setNextActionAt(null);
        run = repository.save(run);
        log.warn("Fertilization {} was interrupted while the backend was down", run.getId());
        if (stillDosing) {
            raise(run, run.getStatus() == FertilizationRun.Status.PARTIAL
                    ? AlertType.FERTILIZATION_PARTIAL : AlertType.FERTILIZATION_SKIPPED, null);
        }
        return new Irrigate(run.getIrrigationScheduleId(), run.getRunDate(), true);
    }

    /** Pemupukan tidak dimulai; dicatat dan diberi peringatan paling banyak sekali per hari per alasan. */
    private void notPoured(
            Long scheduleId, LocalDate today, Instant now, FertilizationRun.Status status, String reasonCode,
            String message, Integer interval) {
        log.warn("Fertilization not started ({}): {}", reasonCode, message);
        if (repository.existsByRunDateAndReasonCode(today, reasonCode)) {
            return;
        }
        FertilizationRun run = new FertilizationRun();
        run.setRunDate(today);
        run.setIrrigationScheduleId(scheduleId);
        run.setIntervalDays(interval);
        run.setStatus(status);
        run.setReasonCode(reasonCode);
        run.setMessage(message);
        run.setStartedAt(now);
        run.setFinishedAt(now);
        run = repository.save(run);
        raise(run, AlertType.FERTILIZATION_SKIPPED, null);
    }

    private void raise(FertilizationRun run, String type, ApiException cause) {
        boolean partial = AlertType.FERTILIZATION_PARTIAL.equals(type);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fertilizationRunId", run.getId());
        metadata.put("status", run.getStatus().name());
        metadata.put("reasonCode", run.getReasonCode());
        metadata.put("irrigationScheduleId", run.getIrrigationScheduleId());
        if (cause != null) {
            metadata.put("errorCode", cause.errorCode().name());
            metadata.putAll(cause.details());
        }
        alertService.raise(NewAlert.builder(type, AlertSeverity.WARNING)
                .title(partial ? "Fertilization only partly done" : "Fertilization did not run")
                .message(partial
                        ? "Some nutrients were poured, then fertilization stopped (%s: %s). Counted as fertilized so it is not doubled."
                                .formatted(run.getReasonCode(), run.getMessage())
                        : "Fertilization due today did not run (%s: %s). It is retried at the next watering session."
                                .formatted(run.getReasonCode(), run.getMessage()))
                .source(AlertSource.SYSTEM)
                .metadata(metadata)
                .build());
    }

    private Optional<Pumps> pumps() {
        Optional<Actuator> n = enabled(properties.actuators().nitrogen());
        Optional<Actuator> p = enabled(properties.actuators().phosphorus());
        Optional<Actuator> k = enabled(properties.actuators().potassium());
        Optional<Actuator> stirrer = enabled(properties.actuators().stirrer());
        if (n.isEmpty() || p.isEmpty() || k.isEmpty() || stirrer.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Pumps(n.get(), p.get(), k.get(), stirrer.get()));
    }

    private Optional<Actuator> enabled(String code) {
        return actuatorRepository.findFirstByCode(code).filter(Actuator::isEnabled);
    }

    private String on(Actuator actuator, int seconds) {
        return commandService.issueSystemCommand(
                        actuator.getId(), new ActuatorCommandRequest("ON", Map.of("durationSeconds", seconds)))
                .commandUid();
    }

    /** Lama aduk: semua dosing + jeda antar pompa + selisih putaran pemeriksaan + {@code mix-seconds}, dibatasi pompa aduk. */
    private int stirrerSeconds(int dosingSeconds, Actuator stirrer) {
        long slack = 2 * properties.pumpGap().toSeconds() + 3 * properties.checkInterval().toSeconds();
        long wanted = dosingSeconds + slack + properties.mixSeconds();
        Integer max = stirrer.getMaxRunSeconds();
        return (int) (max != null && wanted > max ? max : wanted);
    }

    private static int seconds(String label, double aiSeconds, Actuator pump, List<String> capped) {
        int s = (int) Math.max(0, Math.round(aiSeconds));
        Integer max = pump.getMaxRunSeconds();
        if (max != null && s > max) {
            capped.add("%s %d s -> %d s".formatted(label, s, max));
            return max;
        }
        return s;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String reasonCode(ApiException ex) {
        Object interlock = ex.details().get("interlock");
        return interlock != null ? interlock.toString() : ex.errorCode().name();
    }
}
