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

/**
 * A result produced by the vision model running on the edge device.
 *
 * <p>No model runs in the backend and no disease vocabulary is encoded here: {@link #label} is
 * whatever the model reported. The backend stores, serves and broadcasts detections; interpreting
 * them is the AI team's contract.
 */
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

    /**
     * The class the model reported. Free text: the label set belongs to the model, not to us.
     *
     * <p>Null for a multi-label result that only carries {@link #scores}.
     */
    @Column(length = 128)
    private String label;

    /** Model confidence in the range 0..1, as reported. */
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    /**
     * Where the evidence image can be fetched.
     *
     * <p>TODO(hardware/AI): image transport is undecided - the device may upload to object storage
     * and send a URL, or send bytes over a separate channel. Only a reference is modelled for now.
     */
    @Column(name = "image_url", length = 512)
    private String imageUrl;

    /**
     * Label to score map from a multi-label model, for example
     * {@code {"Leaf_N_stress": 0.71, "Leaf_P_stress": 0.12, "Leaf_K_stress": 0.64}}.
     *
     * <p>FERTIMATA Rev A classifies with independent sigmoids so several deficiencies can be high at
     * once; a single label cannot express that. Stored as reported - no threshold is applied here.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String scores;

    /** Camera station the frame came from, for example {@code ST-01}. Free text: the count is not final. */
    @Column(name = "station_code", length = 32)
    private String stationCode;

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
