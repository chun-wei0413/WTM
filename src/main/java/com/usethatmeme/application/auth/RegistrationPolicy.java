package com.usethatmeme.application.auth;

import java.time.Duration;

/**
 * @param enabled whether new accounts may be created at all
 * @param perAddress how many registration attempts one address may make inside the window
 */
public record RegistrationPolicy(boolean enabled, int perAddress, Duration window) {
}
