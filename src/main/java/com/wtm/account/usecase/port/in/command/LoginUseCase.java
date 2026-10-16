package com.wtm.account.usecase.port.in.command;

import com.wtm.shared.TooManyRequestsException;
import java.time.Instant;

public interface LoginUseCase {

    /**
     * @throws TooManyRequestsException when this address has failed too often recently
     */
    Output handle(Input input);

    record Output(String token, Instant expiresAt) {
    }

    /** @param clientAddress where the attempt came from; failed attempts are counted per address */
    record Input(String username, String rawPassword, String clientAddress) {
    }
}
