package com.usethatmeme.application.port.out;

/**
 * Raised by outbound AI adapters when the backing service fails or is unreachable.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
