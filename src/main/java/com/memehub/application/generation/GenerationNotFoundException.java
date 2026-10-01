package com.memehub.application.generation;

import java.util.UUID;

public class GenerationNotFoundException extends RuntimeException {

    public GenerationNotFoundException(UUID id) {
        super("Generation " + id + " not found");
    }
}
