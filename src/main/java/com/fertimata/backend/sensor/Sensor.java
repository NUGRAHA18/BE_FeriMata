package com.fertimata.backend.sensor;

import com.fertimata.backend.common.domain.AuditedEntity;
import com.fertimata.backend.device.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A measurement channel exposed by a device.
 *
 * <p>Sensor kinds (pH, EC, temperature, ...) are <em>data</em>, not schema. They live in
 * {@link #type} and {@link #metricKey}; the hardware team can add, remove or rename a sensor without
 * a database migration and without touching business code.
 */
@Entity
@Table(
        name = "sensor",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_sensor_device_code", columnNames = {"device_id", "code"}),
            @UniqueConstraint(name = "uk_sensor_device_metric", columnNames = {"device_id", "metric_key"})
        })
@Getter
@Setter
@NoArgsConstructor
public class Sensor extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** Asset identifier for operators, unique within a device. */
    @Column(nullable = false, length = 64)
    private String code;

    /**
     * Key this sensor uses inside a telemetry payload, for example {@code temperature}.
     *
     * <p>This is the routing key between an inbound message and a stored reading. It is separate
     * from {@link #code} because an operator-facing asset code and an on-the-wire field name change
     * for different reasons.
     */
    @Column(name = "metric_key", nullable = false, length = 64)
    private String metricKey;

    @Column(nullable = false, length = 128)
    private String name;

    /**
     * Sensor kind as reported or configured, for example {@code TEMPERATURE}.
     *
     * <p>TODO(hardware): free text until the team freezes the sensor catalogue.
     */
    @Column(length = 64)
    private String type;

    /** Unit of the values this sensor produces, for example {@code C} or {@code mS/cm}. */
    @Column(length = 32)
    private String unit;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * True when the sensor row was created automatically from an unrecognised telemetry metric.
     *
     * <p>Such rows start disabled: they preserve incoming data instead of dropping it, but they stay
     * out of dashboards until an operator reviews and names them.
     */
    @Column(name = "auto_registered", nullable = false)
    private boolean autoRegistered = false;

    @Column(length = 255)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    public Sensor(Device device, String code, String metricKey, String name, String type, String unit) {
        this.device = device;
        this.code = code;
        this.metricKey = metricKey;
        this.name = name;
        this.type = type;
        this.unit = unit;
        this.enabled = true;
    }

    @Override
    public String toString() {
        return "Sensor{id=%s, code=%s, metricKey=%s}".formatted(getId(), code, metricKey);
    }
}
