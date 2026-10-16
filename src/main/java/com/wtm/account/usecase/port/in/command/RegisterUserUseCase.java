package com.wtm.account.usecase.port.in.command;

import com.wtm.account.entity.User;
import com.wtm.account.usecase.port.in.RegistrationClosedException;
import com.wtm.account.usecase.port.in.UsernameTakenException;
import com.wtm.shared.TooManyRequestsException;

public interface RegisterUserUseCase {

    /**
     * @throws RegistrationClosedException when registration is switched off
     * @throws TooManyRequestsException when this address registers too often
     * @throws IllegalArgumentException when the username or password is not acceptable
     * @throws UsernameTakenException when another account already uses the name (ignoring case)
     */
    User handle(Input input);

    record Input(String username, String rawPassword, String clientAddress) {
    }
}
