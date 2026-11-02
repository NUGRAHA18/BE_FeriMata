package com.fertimata.backend.fertilization;

import com.fertimata.backend.ai.AiServiceClient;
import com.fertimata.backend.ai.AiServiceException;
import com.fertimata.backend.common.exception.ApiException;
import com.fertimata.backend.common.exception.ErrorCode;
import com.fertimata.backend.common.response.PageResponse;
import com.fertimata.backend.cultivation.CultivationService;
import com.fertimata.backend.fertilization.dto.FertilizationRunResponse;
import com.fertimata.backend.fertilization.dto.FertilizationScheduleResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jadwal pupuk: jarak hari milik layanan AI ({@code dosing_schedules}, satu baris), riwayat jalan milik backend.
 * Jatuh tempo = tanggal DONE/PARTIAL terakhir + {@code interval_days}; belum pernah = jatuh tempo.
 */
@Service
public class FertilizationService {

    private static final Logger log = LoggerFactory.getLogger(FertilizationService.class);

    private final FertilizationRunRepository repository;
    private final AiServiceClient aiClient;
    private final CultivationService cultivationService;
    private final FertilizationProperties properties;
    private final Clock clock;

    public FertilizationService(
            FertilizationRunRepository repository,
            AiServiceClient aiClient,
            CultivationService cultivationService,
            FertilizationProperties properties,
            Clock clock) {
        this.repository = repository;
        this.aiClient = aiClient;
        this.cultivationService = cultivationService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Pemupukan terakhir yang benar-benar menuang (DONE/PARTIAL). */
    @Transactional(readOnly = true)
    public Optional<FertilizationRun> lastFertilized() {
        return repository.findFirstByStatusInOrderByRunDateDescIdDesc(
                EnumSet.of(FertilizationRun.Status.DONE, FertilizationRun.Status.PARTIAL));
    }

    /** Tanggal jatuh tempo berikutnya untuk interval ini. */
    public LocalDate nextDueDate(int intervalDays) {
        return lastFertilized()
                .map(r -> r.getRunDate().plusDays(intervalDays))
                .orElseGet(this::today);
    }

    public boolean due(LocalDate today, int intervalDays) {
        return !nextDueDate(intervalDays).isAfter(today);
    }

    /**
     * Baris jadwal AI yang dipakai. Model AI menyebut "satu baris saja"; bila ternyata lebih dari satu, yang
     * dibuat paling awal (id terkecil) dipakai.
     */
    public static Optional<AiServiceClient.FertilizationInterval> pick(List<AiServiceClient.FertilizationInterval> rows) {
        return rows.stream().min(Comparator.comparingLong(AiServiceClient.FertilizationInterval::id));
    }

    @Transactional(readOnly = true)
    public FertilizationScheduleResponse schedule() {
        Optional<FertilizationRun> last = lastFertilized();
        FertilizationRunResponse active =
                repository.findFirstByStepIsNotNull().map(FertilizationRunResponse::from).orElse(null);
        LocalDate lastDate = last.map(FertilizationRun::getRunDate).orElse(null);
        Instant lastAt = last.map(r -> r.getFinishedAt() != null ? r.getFinishedAt() : r.getStartedAt()).orElse(null);
        if (!aiClient.configured()) {
            return new FertilizationScheduleResponse(properties.enabled(), false, false,
                    "AI_SERVICE_URL is empty", null, null, lastDate, lastAt, null, active);
        }
        Optional<AiServiceClient.FertilizationInterval> row;
        try {
            row = pick(aiClient.fertilizationIntervals());
        } catch (AiServiceException ex) {
            return new FertilizationScheduleResponse(properties.enabled(), true, false, ex.getMessage(), null, null,
                    lastDate, lastAt, null, active);
        }
        return new FertilizationScheduleResponse(
                properties.enabled(),
                true,
                true,
                null,
                row.map(AiServiceClient.FertilizationInterval::id).orElse(null),
                row.map(AiServiceClient.FertilizationInterval::intervalDays).orElse(null),
                lastDate,
                lastAt,
                row.map(r -> lastDate == null ? today() : lastDate.plusDays(r.intervalDays())).orElse(null),
                active);
    }

    /** Menyimpan jarak hari di layanan AI: mengubah baris yang ada, atau membuatnya bila belum ada. */
    public FertilizationScheduleResponse setInterval(int intervalDays) {
        try {
            Optional<AiServiceClient.FertilizationInterval> row = pick(aiClient.fertilizationIntervals());
            if (row.isPresent()) {
                aiClient.updateFertilizationInterval(row.get().id(), intervalDays);
            } else {
                aiClient.createFertilizationInterval(intervalDays);
            }
        } catch (AiServiceException ex) {
            throw unavailable(ex);
        }
        log.info("Fertilization interval set to {} day(s) in the AI service", intervalDays);
        return schedule();
    }

    /** Menghapus jadwal pupuk di layanan AI: pemupukan otomatis berhenti. */
    public FertilizationScheduleResponse deleteInterval() {
        try {
            for (AiServiceClient.FertilizationInterval row : aiClient.fertilizationIntervals()) {
                aiClient.deleteFertilizationInterval(row.id());
            }
        } catch (AiServiceException ex) {
            throw unavailable(ex);
        }
        log.info("Fertilization schedule deleted in the AI service");
        return schedule();
    }

    @Transactional(readOnly = true)
    public PageResponse<FertilizationRunResponse> runs(Pageable pageable) {
        return PageResponse.from(repository.findAllByOrderByRunDateDescIdDesc(pageable), FertilizationRunResponse::from);
    }

    public ZoneId zone() {
        return cultivationService.zone();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }

    private static ApiException unavailable(AiServiceException ex) {
        return new ApiException(ErrorCode.AI_SERVICE_ERROR, ex.getMessage(), ex);
    }
}
