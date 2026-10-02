package com.memehub.application.auth;

import java.time.Duration;

/**
 * How many failed sign-ins are tolerated inside the window.
 *
 * @param perAccount failures for one account from one address (stops guessing one password list)
 * @param perAddress failures from one address across all accounts (stops trying many accounts)
 */
public record LoginPolicy(int perAccount, int perAddress, Duration window) {
}
