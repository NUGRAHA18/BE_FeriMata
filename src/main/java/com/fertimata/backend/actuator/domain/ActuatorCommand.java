package com.fertimata.backend.actuator.domain;

import com.fertimata.backend.common.domain.AuditedEntity;
import com.fertimata.backend.device.Device;
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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An audit record of one attempt to command an actuator.
 *
 * <p>This table is the answer to the operator questions that matter after an incident: who turned
 * this on, when, was it a person or a rule, and did it actually happen. Rows are never deleted or
 * overwritten by a newer command - each request is its own row.
 */
@Entity
@Table(name = "actuator_command")
@Getter
@Setter
@NoArgsConstructor
public class ActuatorCommand extends AuditedEntity {

    /**
     * Correlation id carried on the wire.
     *
     * <p>The device echoes it in its acknowledgement, which is how an ACK is matched back to the
     * command that caused it. A database id is not used for this: it would leak internal
     * identifiers onto the broker and break if history is ever re-keyed.
     */
    @Column(name = "command_uid", nullable = false, unique = true, length = 36)
    private String commandUid = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actuator_id", nullable = false)
    private Actuator actuator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** TODO(hardware): free text until the command vocabulary is agreed with the Jetson team. */
    @Column(name = "command_type", nullable = false, length = 64)
    private String commandType;

    /**
     * Command arguments exactly as submitted, stored as JSON.
     *
     * <p>Kept opaque on purpose: the backend must not grow a column every time the hardware team
     * adds a parameter, and it must not validate units it does not own yet.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String parameters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommandSource source;

    /** Null for AUTOMATION and SYSTEM commands. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommandStatus status = CommandStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    public void markSent(Instant at) {
        this.status = CommandStatus.SENT;
        this.sentAt = at;
    }

    public void markExecuted(Instant at) {
        this.status = CommandStatus.EXECUTED;
        this.executedAt = at;
    }

    public void markFailed(String reason, Instant at) {
        this.status = CommandStatus.FAILED;
        this.errorMessage = truncate(reason);
        this.executedAt = at;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    @Override
    public String toString() {
        return "ActuatorCommand{id=%s, uid=%s, type=%s, status=%s}"
                .formatted(getId(), commandUid, commandType, status);
    }
}
