package com.memehub.adapter.out.render;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("memehub.render")
public record RenderProperties(
        /** Preferred font family; when empty or missing a CJK-capable one is picked automatically. */
        @DefaultValue("") String fontFamily) {
}
