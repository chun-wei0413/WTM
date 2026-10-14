package com.wtm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("wtm.search")
public record SearchProperties(
        /** How much the semantic ranking counts against the keyword ranking (BM25): 0 is keywords only, 1 is meaning only. */
        @DefaultValue("0.7") double denseWeight) {

    public SearchProperties {
        if (denseWeight < 0 || denseWeight > 1) {
            throw new IllegalArgumentException("wtm.search.dense-weight must be between 0 and 1, not " + denseWeight);
        }
    }
}
