package com.fertimata.backend.push.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sama dengan {@code PushSubscription.toJSON()} browser, jadi frontend kirim {@code JSON.stringify(subscription)}
 * apa adanya; field lain (expirationTime) diabaikan. */
@Schema(name = "PushSubscriptionRequest")
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1024) @Schema(example = "https://fcm.googleapis.com/fcm/send/dXk...") String endpoint,
        @NotNull @Valid Keys keys) {

    public record Keys(
            @NotBlank @Size(max = 128) @Schema(description = "base64url, 65 bytes") String p256dh,
            @NotBlank @Size(max = 64) @Schema(description = "base64url, 16 bytes") String auth) {}
}
