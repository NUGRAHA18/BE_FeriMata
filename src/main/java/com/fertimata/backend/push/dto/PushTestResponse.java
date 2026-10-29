package com.fertimata.backend.push.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PushTestResult")
public record PushTestResponse(
        @Schema(description = "Accepted by the push service") int delivered,
        @Schema(description = "Subscriptions the push service no longer knows; removed") int removed,
        @Schema(description = "Temporary failures; kept") int failed) {}
