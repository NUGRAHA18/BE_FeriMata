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

/** Satu sesi siram terjadwal (lihat V21); riwayat untuk melacak error, berbeda dari last_* yang ditimpa. */
@Entity
@Table(name = "irrigation_run")
@Getter
@Setter
@NoArgsConstructor
public class IrrigationRun extends AuditedEntity {

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "irrigation_schedule_id")
    private Long irrigationScheduleId;

    @Column(name = "scheduled_time", nullable = false)
    private LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IrrigationSchedule.RunResult result;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(length = 500)
    private String message;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "command_uid", length = 64)
    private String commandUid;

    /** Waktu hasil ini dicatat (untuk WAITING yang diperbarui: waktu hasil akhirnya). */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
