package com.fertimata.backend.ai;

/** Layanan AI tidak bisa dihubungi atau jawabannya tidak bisa dipakai. */
public class AiServiceException extends Exception {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
