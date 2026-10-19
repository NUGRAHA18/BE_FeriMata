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

/**
 * An edge device that talks to the backend over the message broker.
 *
 * <p>Nothing here assumes a Jetson. The device is identified by an opaque {@code deviceCode} that
 * also appears in its MQTT topics, and everything hardware-specific stays on the device side.
 */
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

    /**
     * Free-form classification, for example {@code EDGE_GATEWAY}.
     *
     * <p>TODO(hardware): kept as free text because the team has not settled on a device taxonomy.
     * Promote to an enum once the set of device kinds is known.
     */
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

    /**
     * Free-form attributes reported by the device (firmware version, agent build, ...).
     *
     * <p>Stored as JSON so that a device reporting a new attribute does not require a migration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    /**
     * Power source the device last reported, for example {@code MAINS} or {@code BATTERY}.
     *
     * <p>Reported, never derived: deciding that a mains voltage reading means an outage is the edge
     * agent's job, because it has to act on it even while the backend is down. Null when the device
     * does not report power at all.
     */
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
