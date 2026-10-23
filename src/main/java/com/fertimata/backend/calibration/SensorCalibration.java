package com.fertimata.backend.calibration;

import com.fertimata.backend.common.domain.BaseEntity;
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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Model koreksi satu sensor terhadap uji laboratorium (Protokol-Data.md §7).
 *
 * <p>Append-only: kalibrasi baru adalah baris baru dengan {@link #validFrom} lebih akhir, sehingga
 * pembacaan lama tetap dikoreksi dengan model yang berlaku saat itu. Nilai mentah tidak pernah diubah.
 */
@Entity
@Table(name = "sensor_calibration")
@Getter
@Setter
@NoArgsConstructor
public class SensorCalibration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CalibrationMethod method;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal intercept;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal slope;

    /** metricKey kovariat -> koefisien. Null untuk {@link CalibrationMethod#LINEAR}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String covariates;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "sample_count")
    private Integer sampleCount;

    /** Bisa sangat negatif untuk model yang lebih buruk dari rata-rata, jadi kolomnya lebar. */
    @Column(name = "r_squared", precision = 18, scale = 6)
    private BigDecimal rSquared;

    @Column(precision = 18, scale = 6)
    private BigDecimal rmse;

    @Column(precision = 18, scale = 6)
    private BigDecimal mae;

    /** Bland-Altman: rata-rata selisih (kalibrasi − lab). */
    @Column(precision = 18, scale = 6)
    private BigDecimal bias;

    @Column(name = "loa_lower", precision = 18, scale = 6)
    private BigDecimal loaLower;

    @Column(name = "loa_upper", precision = 18, scale = 6)
    private BigDecimal loaUpper;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
