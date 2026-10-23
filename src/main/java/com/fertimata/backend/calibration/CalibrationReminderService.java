package com.fertimata.backend.calibration;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertSeverity;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.sensor.SensorRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pengingat uji drift mingguan (Protokol §7) dan kalibrasi pompa bulanan (Protokol §8).
 *
 * <p>Hanya INFO dan sekali per sensor/aktuator sampai ditandai sudah ditangani: ini pekerjaan rutin,
 * bukan bahaya. Daftar sensor dan tipe pompa datang dari konfigurasi.
 */
@Service
public class CalibrationReminderService {

    private final SensorRepository sensorRepository;
    private final ActuatorRepository actuatorRepository;
    private final SensorDriftCheckRepository driftCheckRepository;
    private final ActuatorCalibrationRepository actuatorCalibrationRepository;
    private final AlertService alertService;
    private final CalibrationProperties properties;
    private final Clock clock;

    public CalibrationReminderService(
            SensorRepository sensorRepository,
            ActuatorRepository actuatorRepository,
            SensorDriftCheckRepository driftCheckRepository,
            ActuatorCalibrationRepository actuatorCalibrationRepository,
            AlertService alertService,
            CalibrationProperties properties,
            Clock clock) {
        this.sensorRepository = sensorRepository;
        this.actuatorRepository = actuatorRepository;
        this.driftCheckRepository = driftCheckRepository;
        this.actuatorCalibrationRepository = actuatorCalibrationRepository;
        this.alertService = alertService;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return jumlah pengingat baru yang dimunculkan */
    @Transactional
    public int raiseDueReminders() {
        Instant now = clock.instant();
        int raised = 0;

        Duration driftMaxAge = properties.driftCheck().maxAge();
        for (Sensor sensor : sensorRepository.findAllWithDevice()) {
            if (!sensor.isEnabled() || !properties.driftCheck().metricKeys().contains(sensor.getMetricKey())) {
                continue;
            }
            Optional<Instant> last = driftCheckRepository
                    .findFirstBySensorIdOrderByCheckedAtDesc(sensor.getId())
                    .map(SensorDriftCheck::getCheckedAt);
            if (last.isPresent() && !last.get().isBefore(now.minus(driftMaxAge))) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("metricKey", sensor.getMetricKey());
            metadata.put("lastCheckedAt", last.map(Instant::toString).orElse(null));
            metadata.put("maxAgeDays", driftMaxAge.toDays());
            boolean created = alertService.raiseOnceForSensor(NewAlert.builder(AlertType.DRIFT_CHECK_DUE, AlertSeverity.INFO)
                    .title("Waktunya cek sensor " + sensor.getName())
                    .message(last.isPresent()
                            ? "Sensor ini terakhir dicek dengan larutan standar %d hari lalu. Celupkan ke larutan standar, lalu catat hasilnya."
                                    .formatted(Duration.between(last.get(), now).toDays())
                            : "Sensor ini belum pernah dicek dengan larutan standar. Celupkan ke larutan standar, lalu catat hasilnya.")
                    .device(sensor.getDevice())
                    .sensor(sensor)
                    .metadata(metadata)
                    .build());
            raised += created ? 1 : 0;
        }

        Duration pumpMaxAge = properties.pump().maxAge();
        for (Actuator actuator : actuatorRepository.findAllWithDevice()) {
            if (!actuator.isEnabled() || !properties.pump().covers(actuator.getType())) {
                continue;
            }
            Optional<Instant> last = actuatorCalibrationRepository
                    .findFirstByActuatorIdOrderByCalibratedAtDesc(actuator.getId())
                    .map(ActuatorCalibration::getCalibratedAt);
            if (last.isPresent() && !last.get().isBefore(now.minus(pumpMaxAge))) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("actuatorCode", actuator.getCode());
            metadata.put("lastCalibratedAt", last.map(Instant::toString).orElse(null));
            metadata.put("maxAgeDays", pumpMaxAge.toDays());
            boolean created = alertService.raiseOnceForActuator(NewAlert.builder(AlertType.PUMP_CALIBRATION_DUE, AlertSeverity.INFO)
                    .title("Waktunya ukur ulang " + actuator.getName())
                    .message(last.isPresent()
                            ? "Debit pompa ini terakhir diukur %d hari lalu. Selang bisa melar, ukur lagi dengan gelas ukur (3 kali)."
                                    .formatted(Duration.between(last.get(), now).toDays())
                            : "Debit pompa ini belum pernah diukur, jadi jumlah pupuk yang keluar belum bisa dihitung. Ukur dengan gelas ukur (3 kali).")
                    .device(actuator.getDevice())
                    .actuator(actuator)
                    .metadata(metadata)
                    .build());
            raised += created ? 1 : 0;
        }
        return raised;
    }
}
