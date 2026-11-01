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

/** Everything the main dashboard needs in one response, so the PWA's first screen is a single request. */
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

    /** Siklus tanam aktif hari ini untuk kartu Beranda dan fuzzy dosing; currentPhase null bila HST di luar fase. */
    @Schema(name = "DashboardCultivationSection")
    public record CultivationSection(
            Long cycleId,
            String cycleCode,
            String cycleName,
            LocalDate sowingDate,
            @Deprecated @Schema(description = "Deprecated alias of sowingDate", deprecated = true) LocalDate transplantDate,
            long hst,
            GrowthPhaseResponse currentPhase,
            GrowthPhaseResponse nextPhase,
            Long daysUntilNextPhase,
            long plantCount) {}

    /** Backend runtime info; DEVELOPMENT mode means mock messaging and the simulator may be active. */
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
     * Activity feed line; render from the structured fields, summary/detail are fallbacks. {@code status}: command status
     * or alert severity; {@code actor}: username or command/alert source.
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
            @Schema(description = "Alert id for ALERT entries, command id for ACTUATOR_COMMAND entries") Long refId,
            @Schema(
                            example = "NO_RESPONSE",
                            description = "ACTUATOR_COMMAND: failure code of a FAILED command; ALERT: metadata.failureCode, "
                                    + "or metadata.reasonCode (e.g. BACKUP_POWER for IRRIGATION_SKIPPED); otherwise null")
                    String failureCode,
            @Schema(example = "IRRIGATION_SKIPPED", description = "Alert type for ALERT entries; null for commands")
                    String alertType) {}
}
