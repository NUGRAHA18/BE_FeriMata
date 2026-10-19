package com.fertimata.backend.alert;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.sensor.Sensor;
import java.util.Map;

/**
 * Everything needed to raise an alert.
 *
 * <p>A small builder is used rather than a six-argument service method: most alerts set two or three
 * of these fields and callers should not have to pass nulls for the rest.
 */
public record NewAlert(
        String type,
        AlertSeverity severity,
        String title,
        String message,
        AlertSource source,
        Device relatedDevice,
        Sensor relatedSensor,
        Actuator relatedActuator,
        Map<String, Object> metadata) {

    public static Builder builder(String type, AlertSeverity severity) {
        return new Builder(type, severity);
    }

    public static final class Builder {
        private final String type;
        private final AlertSeverity severity;
        private String title;
        private String message;
        private AlertSource source = AlertSource.SYSTEM;
        private Device relatedDevice;
        private Sensor relatedSensor;
        private Actuator relatedActuator;
        private Map<String, Object> metadata;

        private Builder(String type, AlertSeverity severity) {
            this.type = type;
            this.severity = severity;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder message(String value) {
            this.message = value;
            return this;
        }

        public Builder source(AlertSource value) {
            this.source = value;
            return this;
        }

        public Builder device(Device value) {
            this.relatedDevice = value;
            return this;
        }

        public Builder sensor(Sensor value) {
            this.relatedSensor = value;
            return this;
        }

        public Builder actuator(Actuator value) {
            this.relatedActuator = value;
            return this;
        }

        public Builder metadata(Map<String, Object> value) {
            this.metadata = value;
            return this;
        }

        public NewAlert build() {
            return new NewAlert(
                    type, severity, title, message, source, relatedDevice, relatedSensor, relatedActuator, metadata);
        }
    }
}
