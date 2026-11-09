package com.fertimata.backend.irrigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.domain.ActuatorCommandRepository;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.cultivation.CultivationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IrrigationRunHistoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final Instant NOW = Instant.parse("2026-11-05T23:02:00Z");

    @Mock
    private IrrigationScheduleRepository repository;

    @Mock
    private IrrigationRunRepository runRepository;

    @Mock
    private ActuatorCommandRepository commandRepository;

    @Mock
    private ActuatorRepository actuatorRepository;

    @Mock
    private CultivationService cultivationService;

    @Mock
    private IrrigationProperties properties;

    private IrrigationScheduleService service;
    private IrrigationSchedule schedule;

    @BeforeEach
    void setUp() {
        service = new IrrigationScheduleService(
                repository, runRepository, commandRepository, actuatorRepository, cultivationService, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        schedule = new IrrigationSchedule();
        ReflectionTestUtils.setField(schedule, "id", 1L);
        schedule.setTimeOfDay(LocalTime.of(6, 0));
        schedule.setDurationSeconds(300);
        when(repository.findById(1L)).thenReturn(Optional.of(schedule));
    }

    @Test
    void skippedSessionIsKeptInHistory() {
        when(runRepository.findFirstByIrrigationScheduleIdAndRunDateAndResultOrderByIdDesc(
                        1L, TODAY, IrrigationSchedule.RunResult.WAITING))
                .thenReturn(Optional.empty());

        service.record(1L, TODAY, NOW, IrrigationSchedule.RunResult.SKIPPED, "FLOAT_LOW", "Air drum habis", null);

        ArgumentCaptor<IrrigationRun> saved = ArgumentCaptor.forClass(IrrigationRun.class);
        verify(runRepository).save(saved.capture());
        IrrigationRun run = saved.getValue();
        assertThat(run.getRunDate()).isEqualTo(TODAY);
        assertThat(run.getIrrigationScheduleId()).isEqualTo(1L);
        assertThat(run.getScheduledTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(run.getResult()).isEqualTo(IrrigationSchedule.RunResult.SKIPPED);
        assertThat(run.getReasonCode()).isEqualTo("FLOAT_LOW");
        assertThat(run.getMessage()).isEqualTo("Air drum habis");
        assertThat(run.getRecordedAt()).isEqualTo(NOW);
        assertThat(schedule.getLastResult()).isEqualTo(IrrigationSchedule.RunResult.SKIPPED);
    }

    @Test
    void waitingSessionIsUpdatedWithItsFinalResult() {
        IrrigationRun waiting = new IrrigationRun();
        waiting.setRunDate(TODAY);
        waiting.setIrrigationScheduleId(1L);
        waiting.setScheduledTime(LocalTime.of(6, 0));
        waiting.setResult(IrrigationSchedule.RunResult.WAITING);
        waiting.setReasonCode("FERTILIZING");
        when(runRepository.findFirstByIrrigationScheduleIdAndRunDateAndResultOrderByIdDesc(
                        1L, TODAY, IrrigationSchedule.RunResult.WAITING))
                .thenReturn(Optional.of(waiting));
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant later = NOW.plusSeconds(600);
        service.record(1L, TODAY, later, IrrigationSchedule.RunResult.STARTED, null, null, "cmd-1", 300);

        verify(runRepository).save(waiting);
        assertThat(waiting.getResult()).isEqualTo(IrrigationSchedule.RunResult.STARTED);
        assertThat(waiting.getReasonCode()).isNull();
        assertThat(waiting.getCommandUid()).isEqualTo("cmd-1");
        assertThat(waiting.getDurationSeconds()).isEqualTo(300);
        assertThat(waiting.getRecordedAt()).isEqualTo(later);
    }
}
