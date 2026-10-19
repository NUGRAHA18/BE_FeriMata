package com.fertimata.backend.common.exception;

/**
 * Thrown when the backend cannot hand a message to the messaging infrastructure (broker
 * unreachable, publish rejected, ...).
 *
 * <p>Named after the capability rather than the library, so that replacing MQTT later does not
 * ripple into business code. Deliberately distinct from
 * {@code org.springframework.messaging.MessagingException}: business services should never import
 * messaging-library types.
 */
public class MessagePublishException extends ApiException {

    public MessagePublishException(String message, Throwable cause) {
        super(ErrorCode.MESSAGING_ERROR, message, cause);
    }

    public MessagePublishException(String message) {
        super(ErrorCode.MESSAGING_ERROR, message);
    }
}
