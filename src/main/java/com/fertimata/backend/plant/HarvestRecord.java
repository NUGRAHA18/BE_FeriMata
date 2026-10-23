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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data panen satu buah (Protokol-Data.md §6 "Saat panen").
 *
 * <p>Satu baris per buah: satu tanaman melon bisa dipanen lebih dari satu buah, dan perbandingan
 * FertiMata vs SimonKori diuji per buah. Semua ukuran opsional karena tidak semua alat tersedia tiap hari.
 */
@Entity
@Table(name = "harvest_record")
@Getter
@Setter
@NoArgsConstructor
public class HarvestRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(name = "harvested_on", nullable = false)
    private LocalDate harvestedOn;

    /** HST pada tanggal panen; null bila tanaman tidak punya siklus. */
    @Column
    private Integer hst;

    @Column(name = "fruit_weight_g", precision = 8, scale = 1)
    private BigDecimal fruitWeightG;

    @Column(precision = 5, scale = 2)
    private BigDecimal brix;

    @Column(precision = 8, scale = 2)
    private BigDecimal firmness;

    @Column(name = "firmness_unit", length = 16)
    private String firmnessUnit;

    @Column(name = "flesh_thickness_mm", precision = 6, scale = 1)
    private BigDecimal fleshThicknessMm;

    @Column(name = "dry_matter_percent", precision = 5, scale = 2)
    private BigDecimal dryMatterPercent;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;
}
