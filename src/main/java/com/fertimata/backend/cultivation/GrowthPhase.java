package com.fertimata.backend.cultivation;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Satu fase pertumbuhan dalam siklus tanam, dengan target agronomi yang berlaku selama fase itu.
 *
 * <p>Target EC dan rasio N:P:K adalah masukan fuzzy dosing (Arsitektur-Sistem.md §12); backend
 * menyimpannya sebagai data dan tidak pernah menilai apakah angkanya benar.
 */
@Entity
@Table(name = "growth_phase")
@Getter
@Setter
@NoArgsConstructor
public class GrowthPhase extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private PlantingCycle cycle;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "start_hst", nullable = false)
    private int startHst;

    /** Inklusif. Null berarti berlaku sampai siklus berakhir. */
    @Column(name = "end_hst")
    private Integer endHst;

    @Column(name = "ec_target_min", precision = 6, scale = 3)
    private BigDecimal ecTargetMin;

    @Column(name = "ec_target_max", precision = 6, scale = 3)
    private BigDecimal ecTargetMax;

    @Column(name = "ratio_n", precision = 6, scale = 2)
    private BigDecimal ratioN;

    @Column(name = "ratio_p", precision = 6, scale = 2)
    private BigDecimal ratioP;

    @Column(name = "ratio_k", precision = 6, scale = 2)
    private BigDecimal ratioK;

    @Column(length = 500)
    private String notes;

    public boolean covers(long hst) {
        return hst >= startHst && (endHst == null || hst <= endHst);
    }
}
