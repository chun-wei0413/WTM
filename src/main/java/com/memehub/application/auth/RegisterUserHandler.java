package com.memehub.application.auth;

import com.memehub.application.TooManyRequestsException;
import com.memehub.application.port.out.PasswordHasher;
import com.memehub.application.port.out.RateLimiterPort;
import com.memehub.application.port.out.UserRepository;
import com.memehub.domain.user.Role;
import com.memehub.domain.user.User;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates an ordinary user account. Administrators cannot be created this way.
 */
public class RegisterUserHandler {

    static final int MIN_PASSWORD_LENGTH = 8;
    /** bcrypt ignores everything after 72 bytes, so longer passwords would be silently truncated. */
    static final int MAX_PASSWORD_BYTES = 72;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,32}");

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final RateLimiterPort limiter;
    private final RegistrationPolicy policy;

    public RegisterUserHandler(UserRepository users, PasswordHasher hasher, RateLimiterPort limiter,
                               RegistrationPolicy policy) {
        this.users = users;
        this.hasher = hasher;
        this.limiter = limiter;
        this.policy = policy;
    }

    /**
     * @throws RegistrationClosedException when registration is switched off
     * @throws TooManyRequestsException when this address registers too often
     * @throws IllegalArgumentException when the username or password is not acceptable
     * @throws UsernameTakenException when another account already uses the name (ignoring case)
     */
    public User handle(String username, String rawPassword, String clientAddress) {
        if (!policy.enabled()) {
            throw new RegistrationClosedException();
        }
        if (!limiter.tryAcquire("register:" + clientAddress, policy.perAddress(), policy.window())) {
            throw new TooManyRequestsException(
                    "Too many accounts were created from this address. Please try again later.", policy.window());
        }

        String name = username == null ? "" : username.strip();
        if (!USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "The username must be 3 to 32 characters: letters, digits, '.', '_' or '-'");
        }
        checkPassword(name, rawPassword);

        if (users.findByUsername(name).isPresent()) {
            throw new UsernameTakenException();
        }
        User user = User.register(name, hasher.hash(rawPassword), Role.USER);
        users.save(user);
        return user;
    }

    private static void checkPassword(String username, String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "The password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "The password must be at most " + MAX_PASSWORD_BYTES + " bytes (about 24 Chinese characters)");
        }
        if (password.toLowerCase(Locale.ROOT).equals(username.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("The password must not be the same as the username");
        }
    }
}
