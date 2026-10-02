package com.usethatmeme.application.generation;

import java.util.UUID;

public class MemeNotFoundException extends RuntimeException {

    public MemeNotFoundException(UUID id) {
        super("Meme " + id + " not found");
    }
}
