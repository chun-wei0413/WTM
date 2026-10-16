package com.wtm.account.usecase.service;

import com.wtm.account.entity.Role;
import com.wtm.account.entity.User;
import com.wtm.account.usecase.port.in.command.BootstrapAdminUseCase;
import com.wtm.account.usecase.port.out.PasswordHasher;
import com.wtm.account.usecase.port.out.UserRepository;

/**
 * Creates the first administrator when none exists yet.
 */
public class BootstrapAdminService implements BootstrapAdminUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;

    public BootstrapAdminService(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    /** @return true when an administrator was created */
    @Override
    public boolean handle(Input input) {
        String username = input.username();
        String rawPassword = input.rawPassword();
        if (users.existsByRole(Role.ADMIN)) {
            return false;
        }
        users.save(User.register(username, hasher.hash(rawPassword), Role.ADMIN));
        return true;
    }
}
