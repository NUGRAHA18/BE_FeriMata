package com.fertimata.backend.irrigation;

import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IrrigationScheduleRepository extends JpaRepository<IrrigationSchedule, Long> {

    List<IrrigationSchedule> findAllByOrderByTimeOfDayAsc();

    List<IrrigationSchedule> findByEnabledTrueOrderByTimeOfDayAsc();

    boolean existsByTimeOfDay(LocalTime timeOfDay);
}
