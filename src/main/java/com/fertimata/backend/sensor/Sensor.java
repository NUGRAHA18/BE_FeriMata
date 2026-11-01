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

/** Measurement channel of a device. Kinds are data ({@link #type}, {@link #metricKey}), so a new sensor needs no migration. */
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

    /** Key in the telemetry payload (e.g. {@code temperature}); separate from {@link #code}, they change for different reasons. */
    @Column(name = "metric_key", nullable = false, length = 64)
    private String metricKey;

    @Column(nullable = false, length = 128)
    private String name;

    /** Sensor kind, e.g. {@code TEMPERATURE}. TODO(hardware): free text until the sensor catalogue is frozen. */
    @Column(length = 64)
    private String type;

    /** Unit of the values this sensor produces, for example {@code C} or {@code mS/cm}. */
    @Column(length = 32)
    private String unit;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Auto-created from an unknown metric; starts disabled so data is kept but hidden until an operator reviews it. */
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
