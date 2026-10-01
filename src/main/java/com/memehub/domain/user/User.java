package com.memehub.domain.user;

import java.util.Objects;
import java.util.UUID;

public record User(UUID id, String username, String passwordHash, Role role) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        username = username.strip();
    }

    public static User register(String username, String passwordHash, Role role) {
        return new User(UUID.randomUUID(), username, passwordHash, role);
    }
}
