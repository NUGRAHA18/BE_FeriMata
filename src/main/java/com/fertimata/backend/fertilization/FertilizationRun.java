package com.fertimata.backend.fertilization;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Satu percobaan pemupukan otomatis (lihat V17). */
@Entity
@Table(name = "fertilization_run")
@Getter
@Setter
@NoArgsConstructor
public class FertilizationRun extends AuditedEntity {

    public enum Status {
        /** Pompa dosing/aduk sedang dijalankan; sesi siram menunggu. */
        IN_PROGRESS,
        /** Dosis AI sudah dituang (termasuk bila AI memberi 0 detik untuk semua pompa). */
        DONE,
        /** Sebagian pompa sempat jalan lalu gagal; dihitung sudah dipupuk agar tidak dobel. */
        PARTIAL,
        /** Tidak dituang karena syarat belum terpenuhi (data sensor basi, pompa tidak ada). */
        SKIPPED,
        /** Tidak dituang karena AI tidak bisa dipakai, perintah ditolak, atau backend mati. */
        FAILED;

        /** Dihitung sebagai pemupukan terakhir untuk jatuh tempo berikutnya. */
        public boolean dosed() {
            return this == DONE || this == PARTIAL;
        }
    }

    /** Langkah berikutnya yang dikerjakan {@link FertilizationRunner}. */
    public enum Step {
        PHOSPHORUS,
        POTASSIUM,
        MIXING,
        IRRIGATE
    }

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "irrigation_schedule_id")
    private Long irrigationScheduleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Step step;

    @Column(name = "next_action_at")
    private Instant nextActionAt;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(length = 500)
    private String message;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "ai_nitrogen_seconds", precision = 8, scale = 2)
    private BigDecimal aiNitrogenSeconds;

    @Column(name = "ai_phosphorus_seconds", precision = 8, scale = 2)
    private BigDecimal aiPhosphorusSeconds;

    @Column(name = "ai_potassium_seconds", precision = 8, scale = 2)
    private BigDecimal aiPotassiumSeconds;

    @Column(name = "nitrogen_seconds")
    private Integer nitrogenSeconds;

    @Column(name = "phosphorus_seconds")
    private Integer phosphorusSeconds;

    @Column(name = "potassium_seconds")
    private Integer potassiumSeconds;

    @Column(name = "stirrer_seconds")
    private Integer stirrerSeconds;

    @Column(name = "stirrer_command_uid", length = 64)
    private String stirrerCommandUid;

    @Column(name = "nitrogen_command_uid", length = 64)
    private String nitrogenCommandUid;

    @Column(name = "phosphorus_command_uid", length = 64)
    private String phosphorusCommandUid;

    @Column(name = "potassium_command_uid", length = 64)
    private String potassiumCommandUid;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public void setMessage(String message) {
        this.message = message == null || message.length() <= 500 ? message : message.substring(0, 500);
    }

    /** Ada pompa dosing yang sudah diperintah menyala. */
    public boolean anyDosed() {
        return nitrogenCommandUid != null || phosphorusCommandUid != null || potassiumCommandUid != null;
    }

    @Override
    public String toString() {
        return "FertilizationRun{id=%s, date=%s, status=%s, step=%s}".formatted(getId(), runDate, status, step);
    }
}
