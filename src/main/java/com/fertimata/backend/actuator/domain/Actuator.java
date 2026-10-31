package com.fertimata.backend.actuator.domain;

import com.fertimata.backend.common.domain.AuditedEntity;
import com.fertimata.backend.device.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Something on a device that can be commanded; pump/valve/fan are values of {@link #type}, not subclasses.
 * The backend never knows the wiring, only that it exists and its last reported state. */
@Entity
@Table(
        name = "actuator",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_actuator_device_code", columnNames = {"device_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Actuator extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** TODO(hardware): free text until the actuator catalogue is frozen. */
    @Column(length = 64)
    private String type;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Last device-reported state (e.g. {@code ON}), hardware-owned free text; a command never changes it, an ACK does. */
    @Column(name = "current_state", length = 32)
    private String currentState;

    @Column(name = "state_updated_at")
    private Instant stateUpdatedAt;

    /** Max run of one activating command, in seconds; null = no software limit. Data, not a constant, because it
     * depends on operator calibration (pump flow, rail length); a second line of defence. */
    @Column(name = "max_run_seconds")
    private Integer maxRunSeconds;

    @Column(length = 255)
    private String description;

    /** Informational binding, e.g. {@code {"modbusAddress": 21, "channel": "R0"}}, shown to operators only.
     * The edge agent's address map stays the source of truth; the backend never decides on this. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    public Actuator(Device device, String code, String name, String type) {
        this.device = device;
        this.code = code;
        this.name = name;
        this.type = type;
        this.enabled = true;
    }

    public void reportState(String state, Instant reportedAt) {
        this.currentState = state;
        this.stateUpdatedAt = reportedAt;
    }

    @Override
    public String toString() {
        return "Actuator{id=%s, code=%s, state=%s}".formatted(getId(), code, currentState);
    }
}
