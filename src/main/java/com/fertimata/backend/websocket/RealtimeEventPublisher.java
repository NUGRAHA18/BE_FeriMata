package com.fertimata.backend.websocket;

/**
 * Pushes an event to connected clients.
 *
 * <p>Services depend on this interface, never on {@code SimpMessagingTemplate}, so the realtime
 * transport can change without touching business code.
 */
public interface RealtimeEventPublisher {

    void publish(RealtimeEventType type, Object data);
}
