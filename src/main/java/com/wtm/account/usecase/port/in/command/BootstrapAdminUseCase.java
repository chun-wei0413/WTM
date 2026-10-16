package com.wtm.account.usecase.port.in.command;

public interface BootstrapAdminUseCase {

    /** @return true when an administrator was created */
    boolean handle(String username, String rawPassword);
}
