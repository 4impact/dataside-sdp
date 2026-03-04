package com.fourimpact.sdpsinkconnector.exception;

/**
 * Thrown for transient SDP API failures: 429, 5xx, timeouts.
 * Eligible for retry via @Retryable.
 */
public class TransientSdpException extends RuntimeException {

    public TransientSdpException(String message) {
        super(message);
    }

    public TransientSdpException(String message, Throwable cause) {
        super(message, cause);
    }
}
