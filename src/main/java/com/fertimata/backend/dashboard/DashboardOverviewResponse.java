package com.fertimata.backend.dashboard;

import com.fertimata.backend.actuator.dto.ActuatorStatusResponse;
import com.fertimata.backend.ai.AiDetectionResponse;
import com.fertimata.backend.alert.AlertResponse;
import com.fertimata.backend.cultivation.dto.GrowthPhaseResponse;
import com.fertimata.backend.device.DeviceStatusResponse;
import com.fertimata.backend.telemetry.dto.SensorReadingResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the main dashboard needs, in one response.
 *
 * <p>The shape exists so the PWA can render its first screen with a single request instead of a
 * dozen, and so that adding a panel later is a change here rather than another round-trip.
 */
@Schema(name = "DashboardOverview")
public record DashboardOverviewResponse(
        SystemSection system,
        DeviceSection devices,
        SensorSection sensors,
        List<ActuatorStatusResponse> actuators,
        AlertSection alerts,
        @Schema(description = "Null until the vision model reports something") AiDetectionResponse latestAiDetection,
        List<ActivityEntry> recentActivity,
        @Schema(description = "Null when no planting cycle is active") CultivationSection cultivation) {

    /**
     * Siklus tanam aktif hari ini: umur tanaman dan target fase, untuk kartu Beranda dan fuzzy dosing.
     *
     * @param hst hari setelah transplant
     * @param currentPhase null bila HST di luar fase yang didefinisikan
     */
    @Schema(name = "DashboardCultivationSection")
    public record CultivationSection(
            Long cycleId,
            String cycleCode,
            String cycleName,
            LocalDate transplantDate,
            long hst,
            GrowthPhaseResponse currentPhase,
            GrowthPhaseResponse nextPhase,
            Long daysUntilNextPhase,
            long plantCount,
            long treatmentPlantCount) {}

    /**
     * How the backend itself is running.
     *
     * @param mode DEVELOPMENT means mock messaging and the simulator may be active
     * @param messagingTransport which transport is wired in, so a developer can tell mock from real
     */
    @Schema(name = "DashboardSystemSection")
    public record SystemSection(
            String mode, String messagingTransport, boolean messagingConnected, Instant generatedAt) {}

    @Schema(name = "DashboardDeviceSection")
    public record DeviceSection(long total, long online, long offline, long unknown, List<DeviceStatusResponse> items) {}

    @Schema(name = "DashboardSensorSection")
    public record SensorSection(
            long total,
            long enabled,
            @Schema(description = "Latest value of every enabled sensor") List<SensorReadingResponse> latestReadings) {}

    @Schema(name = "DashboardAlertSection")
    public record AlertSection(long unacknowledged, long critical, long warning, List<AlertResponse> active) {}

    /**
     * One line in the activity feed.
     *
     * <p>{@code summary} and {@code detail} are human-readable fallbacks. The structured fields are
     * what a client should render from, so it never has to parse free text.
     *
     * @param status command status (PENDING / SENT / EXECUTED / FAILED / CANCELLED) for a command entry;
     *     alert severity (INFO / WARNING / CRITICAL) for an alert entry
     * @param actor who caused it: a username, or the command source / alert source
     */
    @Schema(name = "DashboardActivityEntry")
    public record ActivityEntry(
            @Schema(example = "ACTUATOR_COMMAND") String kind,
            Instant at,
            String summary,
            String detail,
            @Schema(example = "EXECUTED") String status,
            @Schema(example = "ON") String commandType,
            @Schema(example = "DIST-PUMP") String actuatorCode,
            @Schema(example = "PANEL-01") String deviceCode,
            @Schema(example = "operator") String actor,
            @Schema(description = "Alert id for ALERT entries, command id for ACTUATOR_COMMAND entries") Long refId) {}
}
