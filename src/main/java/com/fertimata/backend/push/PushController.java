package com.fertimata.backend.push;

import com.fertimata.backend.push.dto.PushConfigResponse;
import com.fertimata.backend.push.dto.PushSubscriptionRequest;
import com.fertimata.backend.push.dto.PushTestResponse;
import com.fertimata.backend.push.dto.PushUnsubscribeRequest;
import com.fertimata.backend.security.auth.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Langganan notifikasi push. Terbuka untuk semua peran, termasuk VIEWER: menerima peringatan darurat
 * di HP tidak mengubah data kebun.
 */
@RestController
@RequestMapping("/api/push")
@Tag(name = "Push notifications", description = "Web Push subscriptions for alert notifications")
@SecurityRequirement(name = "bearerAuth")
public class PushController {

    private final WebPushClient client;
    private final PushSubscriptionService subscriptions;
    private final PushNotificationService notifications;
    private final PushProperties properties;
    private final CurrentUserProvider currentUserProvider;

    public PushController(
            WebPushClient client,
            PushSubscriptionService subscriptions,
            PushNotificationService notifications,
            PushProperties properties,
            CurrentUserProvider currentUserProvider) {
        this.client = client;
        this.subscriptions = subscriptions;
        this.notifications = notifications;
        this.properties = properties;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/config")
    @Operation(
            summary = "Whether push is available and the VAPID public key",
            description = "Pass publicKey as applicationServerKey to pushManager.subscribe().")
    public PushConfigResponse config() {
        return new PushConfigResponse(
                client.enabled(),
                client.publicKey(),
                properties.minSeverity(),
                subscriptions.countForUser(currentUserProvider.requireUsername()));
    }

    @PostMapping("/subscriptions")
    @Operation(
            summary = "Subscribe this browser",
            description = "Body is JSON.stringify(pushSubscription). 201 when new, 200 when it already existed.")
    public ResponseEntity<Void> subscribe(
            @Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        boolean created = subscriptions.subscribe(currentUserProvider.requireUsername(), request, userAgent);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    @DeleteMapping("/subscriptions")
    @Operation(summary = "Unsubscribe this browser", description = "Call it on sign-out and when the user turns notifications off.")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request) {
        subscriptions.unsubscribe(currentUserProvider.requireUsername(), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @Operation(summary = "Send a test notification to every browser of the signed-in account")
    public PushTestResponse test() {
        return notifications.sendTest(currentUserProvider.requireUsername());
    }
}
