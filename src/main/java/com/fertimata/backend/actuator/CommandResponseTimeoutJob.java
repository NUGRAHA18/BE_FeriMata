package com.fertimata.backend.actuator;

import com.fertimata.backend.actuator.domain.ActuatorCommand;
import com.fertimata.backend.actuator.domain.ActuatorCommandRepository;
import com.fertimata.backend.actuator.domain.CommandStatus;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.alert.AlertSource;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.websocket.RealtimeEventPublisher;
import com.fertimata.backend.websocket.RealtimeEventType;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Menutup perintah tak terjawab sebagai FAILED {@code NO_RESPONSE}, bukan dihapus: audit tetap ada
 * dan ACK terlambat masih bisa mengubahnya menjadi EXECUTED. */
@Component
public class CommandResponseTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(CommandResponseTimeoutJob.class);

    private final ActuatorCommandRepository repository;
    private final ActuatorCommandRecorder recorder;
    private final AlertService alertService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final CommandResponseProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CommandResponseTimeoutJob(
            ActuatorCommandRepository repository,
            ActuatorCommandRecorder recorder,
            AlertService alertService,
            RealtimeEventPublisher realtimeEventPublisher,
            CommandResponseProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.recorder = recorder;
        this.alertService = alertService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(
            fixedDelayString = "${app.actuator.response.check-interval}",
            initialDelayString = "${app.actuator.response.check-interval}")
    public void run() {
        int closed = closeUnanswered(clock.instant());
        if (closed > 0) {
            log.warn("{} command(s) got no answer from their device within {}", closed, properties.timeout());
        }
    }

    /** Satu transaksi untuk semua perintah yang ditutup; TransactionTemplate karena self-call dari {@link #run()}
     * melewati {@code @Transactional}. Mengembalikan jumlah yang ditutup. */
    public int closeUnanswered(Instant now) {
        Integer closed = transaction.execute(status -> close(now));
        return closed == null ? 0 : closed;
    }

    private int close(Instant now) {
        List<ActuatorCommand> stale = repository.findByStatusInAndRequestedAtBefore(
                EnumSet.of(CommandStatus.PENDING, CommandStatus.SENT), now.minus(properties.timeout()));
        Instant alertCutoff = now.minus(properties.alertWindow());
        for (ActuatorCommand command : stale) {
            command.markNoResponse(
                    "Alat tidak menjawab dalam %d menit. Perintah mungkin tidak dijalankan; periksa keadaan alat."
                            .formatted(Math.max(1, properties.timeout().toMinutes())),
                    now);
            realtimeEventPublisher.publish(RealtimeEventType.ACTUATOR_COMMAND_UPDATED, recorder.toResponse(command));
            if (command.getRequestedAt().isAfter(alertCutoff)) {
                alertService.raise(NewAlert.builder(AlertType.ACTUATOR_COMMAND_FAILED, AlertSeverity.WARNING)
                        .title("Actuator did not respond")
                        .message("Device %s did not acknowledge command %s for %s"
                                .formatted(
                                        command.getDevice().getDeviceCode(),
                                        command.getCommandType(),
                                        command.getActuator().getCode()))
                        .source(AlertSource.SYSTEM)
                        .actuator(command.getActuator())
                        .device(command.getDevice())
                        .metadata(Map.of(
                                "commandUid", command.getCommandUid(), "failureCode", ActuatorCommand.NO_RESPONSE))
                        .build());
            }
        }
        return stale.size();
    }
}
