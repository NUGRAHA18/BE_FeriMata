package com.fertimata.backend.irrigation;

import com.fertimata.backend.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Satu jam siram harian. */
@Entity
@Table(name = "irrigation_schedule")
@Getter
@Setter
@NoArgsConstructor
public class IrrigationSchedule extends AuditedEntity {

    public enum RunResult {
        /** Perintah siram terkirim ke pompa. */
        STARTED,
        /** Ditolak aturan keselamatan (mis. PLN padam, air drum habis). */
        SKIPPED,
        /** Pompa tidak ditemukan/nonaktif atau perintah gagal terkirim. */
        FAILED,
        /** Backend tidak berjalan pada jam jadwal; siram tidak dikejar terlambat. */
        MISSED
    }

    @Column(name = "time_of_day", nullable = false, unique = true)
    private LocalTime timeOfDay;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 255)
    private String notes;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_result", length = 16)
    private RunResult lastResult;

    @Column(name = "last_message", length = 500)
    private String lastMessage;

    @Column(name = "last_command_uid", length = 64)
    private String lastCommandUid;

    /** Kode alasan untuk SKIPPED/FAILED/MISSED; aplikasi menerjemahkannya, bukan {@link #lastMessage}. */
    @Column(name = "last_reason_code", length = 64)
    private String lastReasonCode;

    /** Durasi yang benar-benar dikirim ke pompa pada jalan terakhir (dari AI atau cadangan). */
    @Column(name = "last_duration_seconds")
    private Integer lastDurationSeconds;

    /** AI, FALLBACK, MANUAL; null bila jalan terakhir tidak sampai menentukan durasi. */
    @Column(name = "last_duration_source", length = 16)
    private String lastDurationSource;

    public void recordRun(
            LocalDate date,
            Instant at,
            RunResult result,
            String reasonCode,
            String message,
            String commandUid,
            Integer durationSeconds,
            String durationSource) {
        recordRun(date, at, result, reasonCode, message, commandUid);
        this.lastDurationSeconds = durationSeconds;
        this.lastDurationSource = durationSource;
    }

    public void recordRun(
            LocalDate date, Instant at, RunResult result, String reasonCode, String message, String commandUid) {
        this.lastRunDate = date;
        this.lastRunAt = at;
        this.lastResult = result;
        this.lastReasonCode = reasonCode;
        this.lastMessage = message == null || message.length() <= 500 ? message : message.substring(0, 500);
        this.lastCommandUid = commandUid;
        this.lastDurationSeconds = null;
        this.lastDurationSource = null;
    }

    @Override
    public String toString() {
        return "IrrigationSchedule{id=%s, at=%s, %ds, enabled=%s}".formatted(getId(), timeOfDay, durationSeconds, enabled);
    }
}
