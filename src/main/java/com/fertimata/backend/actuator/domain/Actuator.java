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

/**
 * Something on a device that can be commanded.
 *
 * <p>Pumps, valves and fans are values of {@link #type}, not subclasses and not tables. The backend
 * never learns how an actuator is wired; it only knows the actuator exists and what state the
 * device last reported for it.
 */
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

    /**
     * Last state reported by the device, for example {@code ON} or {@code OFF}.
     *
     * <p>Free text because the state vocabulary is a hardware decision. This is a reported value,
     * never an assumed one: issuing a command does not change it, an acknowledgement does.
     */
    @Column(name = "current_state", length = 32)
    private String currentState;

    @Column(name = "state_updated_at")
    private Instant stateUpdatedAt;

    /**
     * Longest run one activating command may request, in seconds. Null means no software limit.
     *
     * <p>Data rather than a constant, because it depends on calibration (pump flow, rail length) the
     * operator owns. It is a second line of defence: the hardware design keeps a mechanical timer
     * relay in series with the pumps that no software bug can bypass.
     */
    @Column(name = "max_run_seconds")
    private Integer maxRunSeconds;

    @Column(length = 255)
    private String description;

    /**
     * Informational hardware binding, for example {@code {"modbusAddress": 21, "channel": "R0"}}.
     *
     * <p>Shown to operators only. The edge agent's own address map stays the source of truth for
     * wiring; the backend never reads this to decide anything.
     */
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
