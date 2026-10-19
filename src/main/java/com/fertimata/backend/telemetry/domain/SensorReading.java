package com.fertimata.backend.telemetry.domain;

import com.fertimata.backend.common.domain.BaseEntity;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.sensor.Sensor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One metric from one telemetry message, normalised for querying.
 *
 * <p>Deliberately <em>not</em> one table per sensor kind: a new sensor is a new row here, never a
 * new table or column. The value is split into a numeric and a text column so that a future
 * non-numeric metric (a state string, a quality flag) does not force a schema change either -
 * numeric readings keep the indexable {@link #numericValue} column.
 */
@Entity
@Table(name = "sensor_reading")
@Getter
@Setter
@NoArgsConstructor
public class SensorReading extends BaseEntity {

    /** Payload mentah asal pembacaan ini. Null setelah payload dibersihkan oleh kebijakan retensi. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telemetry_message_id")
    private TelemetryMessage telemetryMessage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    /** Denormalised for the common "everything from this device" query. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** Copied from the sensor at write time so history survives a sensor being renamed. */
    @Column(name = "metric_key", nullable = false, length = 64)
    private String metricKey;

    @Column(name = "numeric_value", precision = 18, scale = 6)
    private BigDecimal numericValue;

    @Column(name = "text_value", length = 255)
    private String textValue;

    @Column(length = 32)
    private String unit;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Override
    public String toString() {
        return "SensorReading{id=%s, metricKey=%s, value=%s}".formatted(getId(), metricKey, numericValue);
    }
}
