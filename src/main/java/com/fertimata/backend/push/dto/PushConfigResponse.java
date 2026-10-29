package com.fertimata.backend.push.dto;

import com.fertimata.backend.alert.AlertSeverity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PushConfig")
public record PushConfigResponse(
        @Schema(description = "False when the server has no VAPID keys; hide the notification toggle") boolean enabled,
        @Schema(description = "applicationServerKey for pushManager.subscribe(); null when disabled") String publicKey,
        @Schema(description = "Alerts of this severity and above are pushed") AlertSeverity minSeverity,
        @Schema(description = "Browsers subscribed for the signed-in account") long subscriptions) {}
