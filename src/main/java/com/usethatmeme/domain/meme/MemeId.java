package com.usethatmeme.domain.meme;

import java.util.Objects;
import java.util.UUID;

public record MemeId(UUID value) {

    public MemeId {
        Objects.requireNonNull(value, "value");
    }

    public static MemeId newId() {
        return new MemeId(UUID.randomUUID());
    }
}
