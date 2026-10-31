package com.fertimata.backend.common.exception;

/** Broker publish failed. Library-neutral name so business code never imports messaging types. */
public class MessagePublishException extends ApiException {

    public MessagePublishException(String message, Throwable cause) {
        super(ErrorCode.MESSAGING_ERROR, message, cause);
    }

    public MessagePublishException(String message) {
        super(ErrorCode.MESSAGING_ERROR, message);
    }
}
