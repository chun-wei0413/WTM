package com.wtm.domain.template;

import java.util.Objects;
import java.util.UUID;

public record TemplateId(UUID value) {

    public TemplateId {
        Objects.requireNonNull(value, "value");
    }

    public static TemplateId newId() {
        return new TemplateId(UUID.randomUUID());
    }
}
