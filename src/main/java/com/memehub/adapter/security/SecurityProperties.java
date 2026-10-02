package com.memehub.adapter.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("memehub.security")
public record SecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue BootstrapAdmin bootstrapAdmin,
        @DefaultValue Throttling throttling,
        /** Set to false to stop anyone from creating accounts (for a private installation). */
        @DefaultValue("true") boolean registrationEnabled) {

    public record Jwt(String secret, @DefaultValue("PT2H") Duration ttl) {
    }

    public record BootstrapAdmin(@DefaultValue("admin") String username, String password) {
    }

    /** Limits that slow down password guessing and mass account creation. */
    public record Throttling(
            /** Failed sign-ins tolerated for one account from one address. */
            @DefaultValue("5") int loginFailuresPerAccount,
            /** Failed sign-ins tolerated from one address across all accounts. */
            @DefaultValue("30") int loginFailuresPerAddress,
            @DefaultValue("PT15M") Duration loginWindow,
            /** Registration attempts one address may make inside the registration window. */
            @DefaultValue("5") int registrationsPerAddress,
            @DefaultValue("PT1H") Duration registrationWindow) {
    }
}
