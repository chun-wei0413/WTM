package com.wtm.shared;

import java.util.Objects;
import java.util.UUID;

/** Who a user is, to every context that has to say whose something is. */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }
}
