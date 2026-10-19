package com.fertimata.backend.websocket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Envelope for every realtime message.
 *
 * <p>A stable envelope means the frontend can route on {@code event} and treat {@code data} as
 * type-specific, so a new event type never breaks an existing client.
 */
@Schema(name = "RealtimeEvent", description = "Envelope pushed over the WebSocket connection")
public record RealtimeEvent<T>(RealtimeEventType event, Instant timestamp, T data) {

    public static <T> RealtimeEvent<T> of(RealtimeEventType type, T data) {
        return new RealtimeEvent<>(type, Instant.now(), data);
    }
}
