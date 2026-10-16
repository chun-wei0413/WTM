package com.wtm.account.entity;

import com.wtm.shared.UserId;
import java.util.Objects;

public record User(UserId id, String username, String passwordHash, Role role) {

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
        return new User(UserId.newId(), username, passwordHash, role);
    }
}
