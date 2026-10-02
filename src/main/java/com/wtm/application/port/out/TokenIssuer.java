package com.wtm.application.port.out;

import com.wtm.domain.user.User;
import java.time.Instant;

public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
