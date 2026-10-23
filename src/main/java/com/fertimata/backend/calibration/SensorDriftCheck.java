package com.fertimata.backend.calibration;

import com.fertimata.backend.common.domain.BaseEntity;
import com.fertimata.backend.sensor.Sensor;
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

/** Uji drift mingguan: probe direndam larutan standar dan simpangannya dicatat (Protokol-Data.md §7 langkah 5). */
@Entity
@Table(name = "sensor_drift_check")
@Getter
@Setter
@NoArgsConstructor
public class SensorDriftCheck extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    /** Mis. "EC 1413 uS/cm" atau "Buffer pH 6,86". */
    @Column(name = "standard_name", nullable = false, length = 64)
    private String standardName;

    @Column(name = "reference_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal referenceValue;

    @Column(name = "measured_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal measuredValue;

    /** measured − reference. */
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal deviation;

    @Column(name = "deviation_percent", precision = 10, scale = 4)
    private BigDecimal deviationPercent;

    @Column(name = "probe_cleaned", nullable = false)
    private boolean probeCleaned;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;
}
