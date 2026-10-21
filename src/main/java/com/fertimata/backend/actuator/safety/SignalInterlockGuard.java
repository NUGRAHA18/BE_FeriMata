package com.fertimata.backend.actuator.safety;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.device.DeviceRepository;
import com.fertimata.backend.sensor.SensorRepository;
import com.fertimata.backend.telemetry.domain.SensorReading;
import com.fertimata.backend.telemetry.domain.SensorReadingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Refuses selected actuator types unless a reported signal is in an allowed state.
 *
 * <p>FERTIMATA Rev A uses this for the drum's float LOW switch: open means low water <em>or</em> a
 * broken wire, and both must keep the distribution pump, the dosing pumps and the stirrer off. A
 * missing or stale reading is treated the same way - the backend cannot tell a quiet sensor from a
 * dry drum. The edge agent enforces the rule locally as well; this guard makes the refusal visible.
 */
@Component
@Order(35)
public class SignalInterlockGuard implements CommandGuard {

    private final ActuatorSafetyProperties properties;
    private final DeviceRepository deviceRepository;
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final Clock clock;

    public SignalInterlockGuard(
            ActuatorSafetyProperties properties,
            DeviceRepository deviceRepository,
            SensorRepository sensorRepository,
            SensorReadingRepository readingRepository,
            Clock clock) {
        this.properties = properties;
        this.deviceRepository = deviceRepository;
        this.sensorRepository = sensorRepository;
        this.readingRepository = readingRepository;
        this.clock = clock;
    }

    @Override
    public void check(CommandContext context) {
        if (!context.activating()) {
            return;
        }
        Actuator actuator = context.actuator();
        for (ActuatorSafetyProperties.SignalInterlock interlock : properties.signalInterlocks()) {
            if (ActuatorSafetyProperties.contains(interlock.blockedTypes(), actuator.getType())) {
                checkSignal(interlock, actuator);
            }
        }
    }

    private void checkSignal(ActuatorSafetyProperties.SignalInterlock interlock, Actuator actuator) {
        Optional<SensorReading> latest = deviceRepository.findByDeviceCode(interlock.device())
                .flatMap(device -> sensorRepository.findByDeviceIdAndMetricKey(device.getId(), interlock.metricKey()))
                .flatMap(sensor -> readingRepository.findFirstBySensorIdOrderByRecordedAtDescIdDesc(sensor.getId()));

        if (latest.isEmpty()) {
            refuse(interlock, actuator, "no reading of %s from %s".formatted(interlock.metricKey(), interlock.device()),
                    null, null);
        }
        SensorReading reading = latest.get();
        String value = value(reading);
        if (interlock.maxAge() != null
                && reading.getRecordedAt().isBefore(Instant.now(clock).minus(interlock.maxAge()))) {
            refuse(interlock, actuator, "%s was last reported at %s, older than %s"
                    .formatted(interlock.metricKey(), reading.getRecordedAt(), interlock.maxAge()), value,
                    reading.getRecordedAt());
        }
        if (value == null || !interlock.okValues().contains(ActuatorSafetyProperties.key(value))) {
            refuse(interlock, actuator, "%s reports %s".formatted(interlock.metricKey(), value), value,
                    reading.getRecordedAt());
        }
    }

    private static String value(SensorReading reading) {
        if (reading.getNumericValue() != null) {
            return reading.getNumericValue().stripTrailingZeros().toPlainString();
        }
        return reading.getTextValue();
    }

    private static void refuse(
            ActuatorSafetyProperties.SignalInterlock interlock,
            Actuator actuator,
            String reason,
            String value,
            Instant recordedAt) {
        Map<String, Object> details = new HashMap<>();
        details.put("interlock", interlock.name());
        details.put("actuatorType", actuator.getType());
        details.put("deviceCode", interlock.device());
        details.put("metricKey", interlock.metricKey());
        if (value != null) {
            details.put("value", value);
        }
        if (recordedAt != null) {
            details.put("recordedAt", recordedAt.toString());
        }
        throw new SafetyInterlockException(
                "%s (%s) is blocked by %s: %s".formatted(actuator.getCode(), actuator.getType(), interlock.name(), reason),
                details);
    }
}
