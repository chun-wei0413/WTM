package com.usethatmeme.application.auth;

public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException() {
        super("That username is already taken");
    }
}
