package com.fertimata.backend.fertilization;

import com.fertimata.backend.irrigation.IrrigationScheduleRunner;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Menjalankan langkah pemupukan yang sudah waktunya, lalu sesi siram yang menunggunya. */
@Component
public class FertilizationRunner {

    private static final Logger log = LoggerFactory.getLogger(FertilizationRunner.class);

    private final FertilizationCoordinator coordinator;
    private final IrrigationScheduleRunner irrigation;
    private final Clock clock;

    public FertilizationRunner(FertilizationCoordinator coordinator, IrrigationScheduleRunner irrigation, Clock clock) {
        this.coordinator = coordinator;
        this.irrigation = irrigation;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.fertilization.check-interval:5s}",
            initialDelayString = "${app.fertilization.check-interval:5s}")
    public void run() {
        advance(clock.instant());
    }

    /** Dipisah dari {@link #run()} agar bisa diuji tanpa scheduler. */
    public void advance(Instant now) {
        try {
            coordinator.advance(now).ifPresent(next -> {
                if (next.scheduleId() == null) {
                    return;
                }
                if (next.missed()) {
                    irrigation.missedAfterFertilization(next.scheduleId(), next.date(), now);
                } else {
                    irrigation.startAfterFertilization(next.scheduleId(), next.date(), now);
                }
            });
        } catch (RuntimeException ex) {
            log.error("Fertilization step failed; retried at the next check", ex);
        }
    }
}
