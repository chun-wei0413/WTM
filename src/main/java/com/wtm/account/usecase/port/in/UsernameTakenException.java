package com.wtm.account.usecase.port.in;

public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException() {
        super("That username is already taken");
    }
}
