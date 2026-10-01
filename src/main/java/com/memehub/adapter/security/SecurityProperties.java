package com.memehub.adapter.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("memehub.security")
public record SecurityProperties(@DefaultValue Jwt jwt, @DefaultValue BootstrapAdmin bootstrapAdmin) {

    public record Jwt(String secret, @DefaultValue("PT2H") Duration ttl) {
    }

    public record BootstrapAdmin(@DefaultValue("admin") String username, String password) {
    }
}
