package com.fertimata.backend.device;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Edge device on the broker, identified by opaque {@code deviceCode} (also in its MQTT topics); not Jetson-specific. */
@Entity
@Table(name = "device")
@Getter
@Setter
@NoArgsConstructor
public class Device extends AuditedEntity {

    /** Stable business identifier, also used to build this device's MQTT topics. */
    @Column(name = "device_code", nullable = false, unique = true, length = 64)
    private String deviceCode;

    @Column(nullable = false, length = 128)
    private String name;

    /** Free-form kind, e.g. {@code EDGE_GATEWAY}. TODO(hardware): enum once the device taxonomy is settled. */
    @Column(length = 64)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceStatus status = DeviceStatus.UNKNOWN;

    /** Last time anything was heard from this device (heartbeat, telemetry or status message). */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(length = 255)
    private String description;

    /** Device-reported attributes (firmware, agent build); JSON so a new attribute needs no migration. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    /** Last reported power source ({@code MAINS}/{@code BATTERY}); reported, never derived. Null if the device does not report it. */
    @Column(name = "power_source", length = 16)
    private String powerSource;

    @Column(name = "power_source_updated_at")
    private Instant powerSourceUpdatedAt;

    public Device(String deviceCode, String name, String type) {
        this.deviceCode = deviceCode;
        this.name = name;
        this.type = type;
        this.status = DeviceStatus.UNKNOWN;
    }

    /** Records contact from the device and marks it online. */
    public void markSeen(Instant seenAt) {
        this.lastSeenAt = seenAt;
        this.status = DeviceStatus.ONLINE;
    }

    public void reportPowerSource(String source, Instant reportedAt) {
        this.powerSource = source;
        this.powerSourceUpdatedAt = reportedAt;
    }

    @Override
    public String toString() {
        return "Device{id=%s, deviceCode=%s, status=%s}".formatted(getId(), deviceCode, status);
    }
}
