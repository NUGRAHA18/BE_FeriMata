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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Satu pasangan pembacaan sensor in-situ dan hasil uji lab dari titik yang sama (Protokol-Data.md §7). */
@Entity
@Table(name = "calibration_sample")
@Getter
@Setter
@NoArgsConstructor
public class CalibrationSample extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "sensor_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal sensorValue;

    @Column(name = "lab_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal labValue;

    @Column(name = "lab_method", length = 64)
    private String labMethod;

    /** metricKey -> nilai kovariat saat sampel diambil. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String covariates;

    @Column(name = "sample_code", length = 64)
    private String sampleCode;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;
}
