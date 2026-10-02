package com.wtm.application.collection;

/**
 * Raised when two pictures with identical content are added at the same moment.
 */
public class DuplicateImageException extends RuntimeException {

    public DuplicateImageException() {
        super("The same image is already in the library");
    }
}
