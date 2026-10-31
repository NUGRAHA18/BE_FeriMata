package com.fertimata.backend.websocket;

/** Pushes events to clients; services depend on this, never on {@code SimpMessagingTemplate}. */
public interface RealtimeEventPublisher {

    void publish(RealtimeEventType type, Object data);
}
