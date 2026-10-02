package com.usethatmeme.application.auth;

import com.usethatmeme.application.port.out.PasswordHasher;
import com.usethatmeme.application.port.out.UserRepository;
import com.usethatmeme.domain.user.Role;
import com.usethatmeme.domain.user.User;

/**
 * Creates the first administrator when none exists yet.
 */
public class BootstrapAdminHandler {

    private final UserRepository users;
    private final PasswordHasher hasher;

    public BootstrapAdminHandler(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    /** @return true when an administrator was created */
    public boolean handle(String username, String rawPassword) {
        if (users.existsByRole(Role.ADMIN)) {
            return false;
        }
        users.save(User.register(username, hasher.hash(rawPassword), Role.ADMIN));
        return true;
    }
}
