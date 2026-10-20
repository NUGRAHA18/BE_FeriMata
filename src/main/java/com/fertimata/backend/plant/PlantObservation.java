package com.fertimata.backend.plant;

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
 * Pengamatan manual satu tanaman (Protokol-Data.md §2, tiap 3-4 hari).
 *
 * <p>Append-only: pengamatan adalah fakta pada satu waktu. {@link #soilSnapshot} menyimpan pembacaan
 * sensor tanah galon itu saat dicatat, supaya catatan visual dan angka tanah selalu sepasang.
 */
@Entity
@Table(name = "plant_observation")
@Getter
@Setter
@NoArgsConstructor
public class PlantObservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    /** HST saat dicatat; null bila tanaman tidak punya siklus. */
    @Column
    private Integer hst;

    @Column(name = "height_cm", precision = 6, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "leaf_count")
    private Integer leafCount;

    @Column(name = "leaf3_length_cm", precision = 6, scale = 1)
    private BigDecimal leaf3LengthCm;

    @Column(name = "leaf3_width_cm", precision = 6, scale = 1)
    private BigDecimal leaf3WidthCm;

    @Column(name = "visual_notes", length = 1000)
    private String visualNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "soil_snapshot", columnDefinition = "jsonb")
    private String soilSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observed_by")
    private User observedBy;
}
