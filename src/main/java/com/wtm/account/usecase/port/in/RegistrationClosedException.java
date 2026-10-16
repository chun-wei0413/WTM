package com.wtm.account.usecase.port.in;

public class RegistrationClosedException extends RuntimeException {

    public RegistrationClosedException() {
        super("Registration is closed");
    }
}
