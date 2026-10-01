package com.memehub.application.auth;

import com.memehub.application.port.out.PasswordHasher;
import com.memehub.application.port.out.TokenIssuer;
import com.memehub.application.port.out.TokenIssuer.IssuedToken;
import com.memehub.application.port.out.UserRepository;
import com.memehub.domain.user.User;
import java.util.Optional;

public class LoginHandler {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;
    private final String dummyHash;

    public LoginHandler(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        // Compared against when the user does not exist, so unknown usernames
        // cost the same as wrong passwords and cannot be told apart by timing.
        this.dummyHash = hasher.hash("memehub-dummy-password");
    }

    public IssuedToken handle(String username, String rawPassword) {
        Optional<User> user = users.findByUsername(username == null ? "" : username.strip());
        String hash = user.map(User::passwordHash).orElse(dummyHash);
        boolean matches = hasher.matches(rawPassword == null ? "" : rawPassword, hash);
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user.get());
    }
}
