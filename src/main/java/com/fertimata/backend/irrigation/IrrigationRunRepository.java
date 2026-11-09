package com.fertimata.backend.irrigation;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IrrigationRunRepository extends JpaRepository<IrrigationRun, Long> {

    /** Sesi yang menunggu pemupukan; hasil akhirnya memperbarui baris ini, bukan menambah baris. */
    Optional<IrrigationRun> findFirstByIrrigationScheduleIdAndRunDateAndResultOrderByIdDesc(
            Long irrigationScheduleId, LocalDate runDate, IrrigationSchedule.RunResult result);

    Page<IrrigationRun> findAllByOrderByRunDateDescIdDesc(Pageable pageable);
}
