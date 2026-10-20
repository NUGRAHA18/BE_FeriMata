package com.fertimata.backend.plant;

import com.fertimata.backend.common.domain.AuditedEntity;
import com.fertimata.backend.cultivation.PlantingCycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Satu tanaman (galon) sebagai unit penelitian.
 *
 * <p>Protokol-Data.md §2 menetapkan 48 tanaman perlakuan (K0 / N- / P- / K-) dalam 4 blok acak dan
 * §6 membagi baris ke dua sistem yang dibandingkan. Semua atribut itu teks bebas: kode perlakuan,
 * blok, dan grup pembanding milik tim penelitian, backend hanya menyimpan dan menyaring.
 */
@Entity
@Table(name = "plant")
@Getter
@Setter
@NoArgsConstructor
public class Plant extends AuditedEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 255)
    private String description;

    /** Null untuk tanaman yang dicatat sebelum ada siklus tanam. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id")
    private PlantingCycle cycle;

    /** K0, N-, P-, K- (Protokol-Data.md §2). Null = tanaman produksi biasa. */
    @Column(name = "treatment_code", length = 16)
    private String treatmentCode;

    @Column(name = "block_code", length = 16)
    private String blockCode;

    @Column(name = "row_code", length = 16)
    private String rowCode;

    /** Sensor tanah yang mewakili galon ini: A untuk {@code soil_a.*}, B untuk {@code soil_b.*}. */
    @Column(name = "plot_code", length = 8)
    private String plotCode;

    /** Stasiun kamera terdekat, mis. ST-03. */
    @Column(name = "station_code", length = 32)
    private String stationCode;

    @Column(name = "position_in_row")
    private Integer positionInRow;

    /** FERTIMATA, SIMONKORI, atau NONE (Protokol-Data.md §6). */
    @Column(name = "comparison_group", length = 16)
    private String comparisonGroup;

    /** Daun yang diberi label, mis. "1,3,5". */
    @Column(name = "tagged_leaves", length = 64)
    private String taggedLeaves;

    @Column(nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    public Plant(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** Awalan metric key sensor tanah untuk galon ini, atau null bila plot belum ditentukan. */
    public String soilMetricPrefix() {
        return plotCode == null || plotCode.isBlank() ? null : "soil_" + plotCode.trim().toLowerCase() + ".";
    }
}
