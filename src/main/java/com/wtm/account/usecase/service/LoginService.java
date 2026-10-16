package com.wtm.account.usecase.service;

import com.wtm.account.entity.LoginPolicy;
import com.wtm.account.entity.User;
import com.wtm.account.usecase.port.in.InvalidCredentialsException;
import com.wtm.account.usecase.port.in.command.LoginUseCase;
import com.wtm.account.usecase.port.out.PasswordHasher;
import com.wtm.account.usecase.port.out.TokenIssuer;
import com.wtm.account.usecase.port.out.TokenIssuer.IssuedToken;
import com.wtm.account.usecase.port.out.UserRepository;
import com.wtm.shared.RateLimiterPort;
import com.wtm.shared.TooManyRequestsException;
import java.util.Locale;
import java.util.Optional;

public class LoginService implements LoginUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;
    private final RateLimiterPort limiter;
    private final LoginPolicy policy;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordHasher hasher, TokenIssuer tokens,
                        RateLimiterPort limiter, LoginPolicy policy) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.limiter = limiter;
        this.policy = policy;
        // Compared against when the user does not exist, so unknown usernames
        // cost the same as wrong passwords and cannot be told apart by timing.
        this.dummyHash = hasher.hash("wtm-dummy-password");
    }

    /**
     * @param clientAddress where the attempt came from; failed attempts are counted per address
     * @throws TooManyRequestsException when this address has failed too often recently
     */
    @Override
    public IssuedToken handle(String username, String rawPassword, String clientAddress) {
        String name = username == null ? "" : username.strip();
        String accountKey = "login:" + clientAddress + "|" + name.toLowerCase(Locale.ROOT);
        String addressKey = "login:" + clientAddress;

        if (limiter.isLimited(accountKey, policy.perAccount(), policy.window())
                || limiter.isLimited(addressKey, policy.perAddress(), policy.window())) {
            throw new TooManyRequestsException(
                    "Too many failed sign-in attempts. Please try again later.", policy.window());
        }

        Optional<User> user = users.findByUsername(name);
        String hash = user.map(User::passwordHash).orElse(dummyHash);
        boolean matches = hasher.matches(rawPassword == null ? "" : rawPassword, hash);
        if (user.isEmpty() || !matches) {
            limiter.tryAcquire(accountKey, policy.perAccount(), policy.window());
            limiter.tryAcquire(addressKey, policy.perAddress(), policy.window());
            throw new InvalidCredentialsException();
        }
        limiter.reset(accountKey);
        return tokens.issue(user.get());
    }
}
