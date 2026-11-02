package com.fertimata.backend.fertilization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.actuator.ActuatorCommandService;
import com.fertimata.backend.actuator.domain.Actuator;
import com.fertimata.backend.actuator.domain.ActuatorRepository;
import com.fertimata.backend.actuator.domain.CommandStatus;
import com.fertimata.backend.actuator.dto.ActuatorCommandRequest;
import com.fertimata.backend.actuator.dto.ActuatorCommandResponse;
import com.fertimata.backend.ai.AiInputsResponse;
import com.fertimata.backend.ai.AiInputsService;
import com.fertimata.backend.ai.AiServiceClient;
import com.fertimata.backend.ai.AiServiceException;
import com.fertimata.backend.alert.AlertService;
import com.fertimata.backend.alert.AlertType;
import com.fertimata.backend.alert.NewAlert;
import com.fertimata.backend.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FertilizationCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-11-01T23:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @Mock
    private FertilizationRunRepository repository;

    @Mock
    private FertilizationService service;

    @Mock
    private AiServiceClient aiClient;

    @Mock
    private AiInputsService aiInputs;

    @Mock
    private ActuatorRepository actuatorRepository;

    @Mock
    private ActuatorCommandService commandService;

    @Mock
    private AlertService alertService;

    private FertilizationCoordinator coordinator;
    private final Map<String, Actuator> actuators = new HashMap<>();
    /** Status ACK per commandUid; bawaan EXECUTED. */
    private final Map<String, CommandStatus> acks = new HashMap<>();
    private final List<String> sent = new ArrayList<>();
    private final AtomicReference<FertilizationRun> active = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        FertilizationProperties properties = new FertilizationProperties(
                true, Duration.ofSeconds(5), 120, Duration.ofSeconds(5), Duration.ofSeconds(15),
                Duration.ofSeconds(90), Duration.ofMinutes(15), null);
        coordinator = new FertilizationCoordinator(repository, service, properties, aiClient, aiInputs,
                actuatorRepository, commandService, alertService);
        long id = 1;
        for (String code : List.of("DOSING-N", "DOSING-P", "DOSING-K", "STIRRER")) {
            Actuator actuator = TestFixtures.actuator(id++, TestFixtures.device(1L, "RIO-DOSING-01"), code);
            actuator.setMaxRunSeconds(code.equals("STIRRER") ? 600 : 150);
            actuator.reportState("OFF", NOW);
            actuators.put(code, actuator);
        }
        lenient().when(actuatorRepository.findFirstByCode(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(actuators.get(inv.<String>getArgument(0))));
        lenient().when(aiClient.configured()).thenReturn(true);
        lenient().when(repository.save(any())).thenAnswer(inv -> {
            FertilizationRun run = inv.getArgument(0);
            if (run.getId() == null) {
                TestFixtures.setId(run, 77L);
            }
            active.set(run.getStep() != null ? run : null);
            return run;
        });
        lenient().when(repository.findFirstByStepIsNotNull()).thenAnswer(inv -> Optional.ofNullable(active.get()));
        lenient().when(commandService.issueSystemCommand(any(), any())).thenAnswer(inv -> {
            Long actuatorId = inv.getArgument(0);
            ActuatorCommandRequest request = inv.getArgument(1);
            String code = actuators.values().stream().filter(a -> a.getId().equals(actuatorId)).findFirst()
                    .orElseThrow().getCode();
            String uid = "cmd-%s-%s-%d".formatted(code, request.command(), sent.size());
            sent.add(code + " " + request.command() + " " + request.parameters().getOrDefault("durationSeconds", ""));
            return command(uid, CommandStatus.SENT, null);
        });
        lenient().when(commandService.byUid(anyString())).thenAnswer(inv -> {
            String uid = inv.getArgument(0);
            CommandStatus status = acks.getOrDefault(uid, CommandStatus.EXECUTED);
            return command(uid, status, status == CommandStatus.FAILED ? "INTERLOCK_FLOAT" : null);
        });
    }

    private static ActuatorCommandResponse command(String uid, CommandStatus status, String failureCode) {
        return new ActuatorCommandResponse(1L, uid, 1L, "X", 1L, "RIO", "ON", Map.of(), null, null, status, NOW, NOW,
                null, failureCode == null ? null : "REASON: drum low", failureCode);
    }

    private void dueWithReadyInputs() throws AiServiceException {
        when(aiClient.fertilizationIntervals()).thenReturn(List.of(new AiServiceClient.FertilizationInterval(3, 7)));
        when(service.due(TODAY, 7)).thenReturn(true);
        when(aiInputs.inputs()).thenReturn(inputs(true));
    }

    private static AiInputsResponse inputs(boolean ready) {
        Map<String, BigDecimal> form = new HashMap<>();
        form.put("ec", ready ? new BigDecimal("1.8") : null);
        return new AiInputsResponse(NOW, 900, 20, null, null, ready, true, form, Map.of(), Map.of(),
                ready ? List.of() : List.of("fertilization.ec (tank.ec): stale, 1820 s old"));
    }

    @Test
    @DisplayName("not due yet: the watering goes ahead and no pump is touched")
    void notDue() throws Exception {
        when(aiClient.fertilizationIntervals()).thenReturn(List.of(new AiServiceClient.FertilizationInterval(3, 7)));
        when(service.due(TODAY, 7)).thenReturn(false);

        assertThat(coordinator.beforeIrrigation(1L, TODAY, NOW)).isEqualTo(FertilizationCoordinator.Decision.PROCEED);

        verify(commandService, never()).issueSystemCommand(any(), any());
        verify(aiClient, never()).fertilizationDose(any());
    }

    @Test
    @DisplayName("no AI dosing schedule: nothing to do")
    void noSchedule() throws Exception {
        when(aiClient.fertilizationIntervals()).thenReturn(List.of());

        assertThat(coordinator.beforeIrrigation(1L, TODAY, NOW)).isEqualTo(FertilizationCoordinator.Decision.PROCEED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("S9: stale EC/pH means no dosing, a SKIPPED run and one warning per day")
    void staleInputsSkip() throws Exception {
        when(aiClient.fertilizationIntervals()).thenReturn(List.of(new AiServiceClient.FertilizationInterval(3, 7)));
        when(service.due(TODAY, 7)).thenReturn(true);
        when(aiInputs.inputs()).thenReturn(inputs(false));

        assertThat(coordinator.beforeIrrigation(1L, TODAY, NOW)).isEqualTo(FertilizationCoordinator.Decision.PROCEED);

        ArgumentCaptor<FertilizationRun> run = ArgumentCaptor.forClass(FertilizationRun.class);
        verify(repository).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo(FertilizationRun.Status.SKIPPED);
        assertThat(run.getValue().getReasonCode()).isEqualTo("AI_INPUTS_NOT_READY");
        verify(aiClient, never()).fertilizationDose(any());
        ArgumentCaptor<NewAlert> alert = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raise(alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(AlertType.FERTILIZATION_SKIPPED);
    }

    @Test
    @DisplayName("full run: stirrer + N now, P skipped at 0 s, K capped to 150 s, mixing, then the waiting session waters")
    void fullRun() throws Exception {
        dueWithReadyInputs();
        when(aiClient.fertilizationDose(any())).thenReturn(new AiServiceClient.Dose(10.4, 0, 200));

        assertThat(coordinator.beforeIrrigation(1L, TODAY, NOW)).isEqualTo(FertilizationCoordinator.Decision.DEFERRED);
        FertilizationRun run = active.get();
        assertThat(run.getNitrogenSeconds()).isEqualTo(10);
        assertThat(run.getPhosphorusSeconds()).isZero();
        assertThat(run.getPotassiumSeconds()).isEqualTo(150);
        assertThat(run.getReasonCode()).isEqualTo("AI_CAPPED");
        // 160 s dosing + 2x5 s gap + 3x5 s check slack + 120 s mixing.
        assertThat(run.getStirrerSeconds()).isEqualTo(305);
        assertThat(sent).containsExactly("STIRRER ON 305", "DOSING-N ON 10");
        assertThat(run.getStep()).isEqualTo(FertilizationRun.Step.PHOSPHORUS);
        assertThat(run.getNextActionAt()).isEqualTo(NOW.plusSeconds(15));

        // Belum waktunya.
        assertThat(coordinator.advance(NOW.plusSeconds(10))).isEmpty();
        assertThat(sent).hasSize(2);

        // N selesai dan OFF: P (0 s) dilewati, K langsung.
        Instant t1 = NOW.plusSeconds(15);
        assertThat(coordinator.advance(t1)).isEmpty();
        assertThat(sent).containsExactly("STIRRER ON 305", "DOSING-N ON 10", "DOSING-K ON 150");
        assertThat(run.getStep()).isEqualTo(FertilizationRun.Step.MIXING);
        // Aduk berakhir NOW+305, lalu jeda 15 s.
        assertThat(run.getNextActionAt()).isEqualTo(NOW.plusSeconds(320));

        Optional<FertilizationCoordinator.Irrigate> irrigate = coordinator.advance(NOW.plusSeconds(320));
        assertThat(irrigate).contains(new FertilizationCoordinator.Irrigate(1L, TODAY, false));
        assertThat(run.getStatus()).isEqualTo(FertilizationRun.Status.DONE);
        assertThat(run.getStep()).isNull();
        verify(alertService, never()).raise(any());
    }

    @Test
    @DisplayName("the next pump waits while the previous one still reports ON (edge allows one dosing pump at a time)")
    void waitsForPreviousPumpOff() throws Exception {
        dueWithReadyInputs();
        when(aiClient.fertilizationDose(any())).thenReturn(new AiServiceClient.Dose(10, 20, 30));
        coordinator.beforeIrrigation(1L, TODAY, NOW);
        actuators.get("DOSING-N").reportState("ON", NOW);

        assertThat(coordinator.advance(NOW.plusSeconds(15))).isEmpty();
        assertThat(sent).hasSize(2);

        actuators.get("DOSING-N").reportState("OFF", NOW.plusSeconds(16));
        coordinator.advance(NOW.plusSeconds(20));
        assertThat(sent).last().isEqualTo("DOSING-P ON 20");
    }

    @Test
    @DisplayName("edge refuses P mid-run: stirrer off, PARTIAL (N was poured), then the session still waters")
    void refusedMidRun() throws Exception {
        dueWithReadyInputs();
        when(aiClient.fertilizationDose(any())).thenReturn(new AiServiceClient.Dose(10, 20, 30));
        coordinator.beforeIrrigation(1L, TODAY, NOW);
        coordinator.advance(NOW.plusSeconds(15));
        FertilizationRun run = active.get();
        assertThat(sent).last().isEqualTo("DOSING-P ON 20");
        acks.put(run.getPhosphorusCommandUid(), CommandStatus.FAILED);

        assertThat(coordinator.advance(NOW.plusSeconds(40))).isEmpty();

        assertThat(sent).last().isEqualTo("STIRRER OFF ");
        assertThat(run.getStatus()).isEqualTo(FertilizationRun.Status.PARTIAL);
        assertThat(run.getReasonCode()).isEqualTo("INTERLOCK_FLOAT");
        assertThat(run.getPhosphorusCommandUid()).isNull();
        assertThat(run.getStep()).isEqualTo(FertilizationRun.Step.IRRIGATE);
        ArgumentCaptor<NewAlert> alert = ArgumentCaptor.forClass(NewAlert.class);
        verify(alertService).raise(alert.capture());
        assertThat(alert.getValue().type()).isEqualTo(AlertType.FERTILIZATION_PARTIAL);

        assertThat(coordinator.advance(NOW.plusSeconds(55)))
                .contains(new FertilizationCoordinator.Irrigate(1L, TODAY, false));
        assertThat(sent).noneMatch(s -> s.startsWith("DOSING-K"));
    }

    @Test
    @DisplayName("the AI asks for no dose: counted as fertilized, the watering goes ahead")
    void zeroDose() throws Exception {
        dueWithReadyInputs();
        when(aiClient.fertilizationDose(any())).thenReturn(new AiServiceClient.Dose(0, 0.2, 0));

        assertThat(coordinator.beforeIrrigation(1L, TODAY, NOW)).isEqualTo(FertilizationCoordinator.Decision.PROCEED);

        ArgumentCaptor<FertilizationRun> run = ArgumentCaptor.forClass(FertilizationRun.class);
        verify(repository).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo(FertilizationRun.Status.DONE);
        assertThat(run.getValue().getReasonCode()).isEqualTo("AI_NO_DOSE_NEEDED");
        verify(commandService, never()).issueSystemCommand(any(), any());
    }

    @Test
    @DisplayName("while a run is in progress another due session is told BUSY")
    void busy() {
        FertilizationRun run = new FertilizationRun();
        run.setStep(FertilizationRun.Step.MIXING);
        active.set(run);

        assertThat(coordinator.beforeIrrigation(2L, TODAY, NOW)).isEqualTo(FertilizationCoordinator.Decision.BUSY);
    }

    @Test
    @DisplayName("backend down for longer than grace: the run is closed and the session is reported missed")
    void backendWasDown() throws Exception {
        dueWithReadyInputs();
        when(aiClient.fertilizationDose(any())).thenReturn(new AiServiceClient.Dose(10, 20, 30));
        coordinator.beforeIrrigation(1L, TODAY, NOW);
        FertilizationRun run = active.get();

        assertThat(coordinator.advance(NOW.plus(Duration.ofHours(1))))
                .contains(new FertilizationCoordinator.Irrigate(1L, TODAY, true));
        assertThat(run.getStatus()).isEqualTo(FertilizationRun.Status.PARTIAL);
        assertThat(run.getReasonCode()).isEqualTo("BACKEND_DOWN");
        verify(alertService).raise(any());
        verify(commandService, never()).issueSystemCommand(eq(2L), any());
    }
}
