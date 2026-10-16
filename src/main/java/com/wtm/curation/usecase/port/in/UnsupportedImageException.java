package com.wtm.curation.usecase.port.in;

public class UnsupportedImageException extends RuntimeException {

    public UnsupportedImageException(String message) {
        super(message);
    }
}
