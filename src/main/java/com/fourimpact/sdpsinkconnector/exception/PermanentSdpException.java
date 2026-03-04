package com.fourimpact.sdpsinkconnector.exception;

/**
 * Thrown for permanent SDP API failures: 4xx (except 429), schema errors.
 * Not retried — message is sent directly to DLQ.
 */
public class PermanentSdpException extends RuntimeException {

    public PermanentSdpException(String message) {
        super(message);
    }

    public PermanentSdpException(String message, Throwable cause) {
        super(message, cause);
    }
}
