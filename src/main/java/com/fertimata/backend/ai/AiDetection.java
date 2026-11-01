package com.fertimata.backend.ai;

import com.fertimata.backend.common.domain.BaseEntity;
import com.fertimata.backend.device.Device;
import com.fertimata.backend.plant.Plant;
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

/** A vision-model result from the edge; no model or disease vocabulary in the backend, {@link #label} is as reported. */
@Entity
@Table(name = "ai_detection")
@Getter
@Setter
@NoArgsConstructor
public class AiDetection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** Nullable: per-plant tracking is not a settled requirement yet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private Plant plant;

    /** What kind of inference this was, for example {@code DISEASE} or {@code RIPENESS}. */
    @Column(name = "detection_type", nullable = false, length = 64)
    private String detectionType;

    /** Model-reported class, free text; null for a multi-label result that only carries {@link #scores}. */
    @Column(length = 128)
    private String label;

    /** Model confidence in the range 0..1, as reported. */
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    /** Evidence image reference. TODO(hardware/AI): image transport undecided (object-storage URL or separate channel). */
    @Column(name = "image_url", length = 512)
    private String imageUrl;

    /** Multi-label scores, e.g. {@code {"Leaf_N_stress": 0.71, "Leaf_K_stress": 0.64}}; Rev A uses independent sigmoids
     * so several deficiencies can be high at once. Stored as reported, no threshold applied. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String scores;

    /** Camera station the frame came from, ST-01 to ST-10 on Rev A. Free text, so a new layout needs no migration. */
    @Column(name = "station_code", length = 32)
    private String stationCode;

    /** Which way the camera faced at that station: TIMUR or BARAT on Rev A. Stored as reported, upper-cased. */
    @Column(name = "pan_direction", length = 16)
    private String panDirection;

    /** Groups every result produced from one capture run or frame. */
    @Column(name = "capture_id", length = 64)
    private String captureId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Override
    public String toString() {
        return "AiDetection{id=%s, type=%s, label=%s}".formatted(getId(), detectionType, label);
    }
}
