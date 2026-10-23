package com.fertimata.backend.calibration;

import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.common.domain.BaseEntity;
import com.fertimata.backend.user.User;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Debit terukur satu pompa (Protokol-Data.md §8).
 *
 * <p>Append-only: debit yang berlaku untuk sebuah perintah adalah kalibrasi terakhir sebelum perintah
 * itu dijalankan, jadi volume pupuk masa lalu tidak berubah saat pompa dikalibrasi ulang.
 */
@Entity
@Table(name = "actuator_calibration")
@Getter
@Setter
@NoArgsConstructor
public class ActuatorCalibration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actuator_id", nullable = false)
    private Actuator actuator;

    @Column(name = "calibrated_at", nullable = false)
    private Instant calibratedAt;

    @Column(name = "flow_ml_per_second", nullable = false, precision = 12, scale = 6)
    private BigDecimal flowMlPerSecond;

    @Column(name = "trial_count", nullable = false)
    private int trialCount;

    /** [{durationSeconds, volumeMl}] bila ulangan dicatat satu per satu. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String trials;

    /** Koefisien variasi debit antar ulangan, %. Null bila kurang dari dua ulangan tercatat. */
    @Column(name = "flow_cv_percent", precision = 8, scale = 4)
    private BigDecimal flowCvPercent;

    @Column(name = "bus_voltage", precision = 8, scale = 2)
    private BigDecimal busVoltage;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;
}
