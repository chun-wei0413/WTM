package com.usethatmeme.application.auth;

public class RegistrationClosedException extends RuntimeException {

    public RegistrationClosedException() {
        super("Registration is closed");
    }
}
