package com.fertimata.backend.calibration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Menjalankan {@link CalibrationReminderService} secara berkala. */
@Component
@ConditionalOnProperty(prefix = "app.calibration.reminders", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CalibrationReminderJob {

    private static final Logger log = LoggerFactory.getLogger(CalibrationReminderJob.class);

    private final CalibrationReminderService service;

    public CalibrationReminderJob(CalibrationReminderService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${app.calibration.reminders.interval:6h}",
            initialDelayString = "${app.calibration.reminders.initial-delay:5m}")
    public void run() {
        int raised = service.raiseDueReminders();
        if (raised > 0) {
            log.info("Calibration reminders raised: {}", raised);
        }
    }
}
