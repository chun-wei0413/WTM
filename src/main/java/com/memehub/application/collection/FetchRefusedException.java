package com.memehub.application.collection;

/**
 * A download was not done on purpose: the site's robots.txt forbids it, the address points at a
 * private network, the file is too large or not a picture, or the site answered with an error.
 */
public class FetchRefusedException extends RuntimeException {

    public FetchRefusedException(String message) {
        super(message);
    }

    public FetchRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
