package com.wtm.application.port.out;

/**
 * Raised when the object storage cannot be reached or rejects a request.
 */
public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
