package com.wtm.account.usecase.port.out;

import com.wtm.account.entity.User;
import java.time.Instant;

public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
