package com.fertimata.backend.fertilization;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Takaran pupuk (keputusan 4 Nov 2026): AI bawaan, atau ml isian petani. Satu baris; belum ada = AI. */
@Entity
@Table(name = "fertilization_dose_setting")
@Getter
@Setter
@NoArgsConstructor
public class FertilizationDoseSetting extends AuditedEntity {

    public enum Mode {
        /** Lama pompa N/P/K dari fuzzy {@code run_fertilization}. */
        AI,
        /** Mililiter isian petani, diubah ke detik dengan kalibrasi debit pompa. */
        MANUAL
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Mode mode = Mode.AI;

    @Column(name = "nitrogen_ml", precision = 8, scale = 2)
    private BigDecimal nitrogenMl;

    @Column(name = "phosphorus_ml", precision = 8, scale = 2)
    private BigDecimal phosphorusMl;

    @Column(name = "potassium_ml", precision = 8, scale = 2)
    private BigDecimal potassiumMl;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
