package com.fertimata.backend.telemetry.domain;

import com.fertimata.backend.common.domain.BaseEntity;
import com.fertimata.backend.device.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The envelope of one inbound telemetry message, stored verbatim.
 *
 * <p>This is half of the telemetry model. The raw {@code payload} is kept so that a message can be
 * inspected or replayed after the fact - which matters while the payload contract is still moving -
 * and the individual metrics are additionally normalised into {@link SensorReading} rows so that
 * history queries stay cheap.
 *
 * <p>A message is recorded even when it is rejected: a payload the backend could not understand is
 * exactly the payload someone will need to look at.
 */
@Entity
@Table(name = "telemetry_message")
@Getter
@Setter
@NoArgsConstructor
public class TelemetryMessage extends BaseEntity {

    /** Null when the payload referenced a device that is not registered. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    /** Always recorded, even when no device row matches, so bad traffic can be traced. */
    @Column(name = "device_code", nullable = false, length = 64)
    private String deviceCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TelemetrySource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TelemetryStatus status;

    /** Why the message was rejected or only partially accepted. Null on success. */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** Timestamp supplied by the device, or the receive time when the device supplied none. */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Server-side arrival time. Always trustworthy, unlike a device clock. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "metric_count", nullable = false)
    private int metricCount;

    @Override
    public String toString() {
        return "TelemetryMessage{id=%s, deviceCode=%s, status=%s, metrics=%d}"
                .formatted(getId(), deviceCode, status, metricCount);
    }
}
