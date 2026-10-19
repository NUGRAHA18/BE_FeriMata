package com.fertimata.backend.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Aggregated view for the operator console")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    @Operation(
            summary = "Everything the main dashboard needs, in one request",
            description =
                    """
                    Returns system status, device liveness, the latest reading of every enabled sensor,
                    actuator states, open alerts, the latest AI detection and a recent activity feed.
                    Live updates arrive over the WebSocket connection; this endpoint is for the initial
                    render and for reconnects.
                    """)
    public DashboardOverviewResponse overview() {
        return dashboardService.overview();
    }
}
