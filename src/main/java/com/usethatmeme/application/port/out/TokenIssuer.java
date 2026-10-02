package com.usethatmeme.application.port.out;

import com.usethatmeme.domain.user.User;
import java.time.Instant;

public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
