package com.wtm.account.usecase.port.in.command;

public interface BootstrapAdminUseCase {

    /** @return true when an administrator was created */
    boolean handle(Input input);

    record Input(String username, String rawPassword) {
    }
}
