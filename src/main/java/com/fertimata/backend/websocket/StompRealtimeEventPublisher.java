package com.fertimata.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** STOMP implementation of {@link RealtimeEventPublisher}. */
@Component
public class StompRealtimeEventPublisher implements RealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StompRealtimeEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public StompRealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(RealtimeEventType type, Object data) {
        RealtimeEvent<Object> event = RealtimeEvent.of(type, data);
        try {
            messagingTemplate.convertAndSend(type.destination(), event);
            messagingTemplate.convertAndSend(RealtimeEventType.FIREHOSE_DESTINATION, event);
        } catch (RuntimeException ex) {
            // A realtime push is a convenience. Losing it must never fail the transaction that
            // already persisted the underlying fact.
            log.warn("Could not broadcast {} event: {}", type, ex.getMessage());
        }
    }
}
