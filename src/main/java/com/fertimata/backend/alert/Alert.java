package com.fertimata.backend.alert;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.common.domain.BaseEntity;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.sensor.Sensor;
import com.fertimata.backend.user.User;
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
 * Something an operator should look at.
 *
 * <p>Only infrastructure-level alerts are produced in this phase (a device going quiet, a command
 * failing). Agronomic alerts need thresholds the team has not agreed on; the domain is ready for
 * them, the rules are not written.
 */
@Entity
@Table(name = "alert")
@Getter
@Setter
@NoArgsConstructor
public class Alert extends BaseEntity {

    /** See {@link AlertType} for the identifiers the backend itself raises. */
    @Column(nullable = false, length = 64)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertSeverity severity;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_device_id")
    private Device relatedDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_sensor_id")
    private Sensor relatedSensor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_actuator_id")
    private Actuator relatedActuator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false)
    private boolean acknowledged = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    public void acknowledge(User user, Instant at) {
        this.acknowledged = true;
        this.acknowledgedBy = user;
        this.acknowledgedAt = at;
    }

    @Override
    public String toString() {
        return "Alert{id=%s, type=%s, severity=%s}".formatted(getId(), type, severity);
    }
}
