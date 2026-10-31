package com.fertimata.backend.websocket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Stable envelope for every realtime message: clients route on {@code event}, so a new type never breaks them. */
@Schema(name = "RealtimeEvent", description = "Envelope pushed over the WebSocket connection")
public record RealtimeEvent<T>(RealtimeEventType event, Instant timestamp, T data) {

    public static <T> RealtimeEvent<T> of(RealtimeEventType type, T data) {
        return new RealtimeEvent<>(type, Instant.now(), data);
    }
}
