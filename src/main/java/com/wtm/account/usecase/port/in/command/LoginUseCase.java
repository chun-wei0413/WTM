package com.wtm.account.usecase.port.in.command;

import com.wtm.account.usecase.port.out.TokenIssuer.IssuedToken;
import com.wtm.shared.TooManyRequestsException;

public interface LoginUseCase {

    /**
     * @throws TooManyRequestsException when this address has failed too often recently
     */
    IssuedToken handle(Input input);

    /** @param clientAddress where the attempt came from; failed attempts are counted per address */
    record Input(String username, String rawPassword, String clientAddress) {
    }
}
