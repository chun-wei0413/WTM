package com.memehub.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memehub.application.port.out.PasswordHasher;
import com.memehub.application.port.out.TokenIssuer;
import com.memehub.application.port.out.TokenIssuer.IssuedToken;
import com.memehub.application.port.out.UserRepository;
import com.memehub.domain.user.Role;
import com.memehub.domain.user.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginHandlerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final TokenIssuer tokens = mock(TokenIssuer.class);
    private LoginHandler handler;

    @BeforeEach
    void setUp() {
        when(hasher.hash(anyString())).thenReturn("dummy-hash");
        handler = new LoginHandler(users, hasher, tokens);
    }

    @Test
    void issuesTokenWhenCredentialsMatch() {
        User user = User.register("frank", "stored-hash", Role.ADMIN);
        IssuedToken token = new IssuedToken("jwt", Instant.now());
        when(users.findByUsername("frank")).thenReturn(Optional.of(user));
        when(hasher.matches("secret", "stored-hash")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(token);

        assertThat(handler.handle("frank", "secret")).isSameAs(token);
    }

    @Test
    void rejectsWrongPassword() {
        User user = User.register("frank", "stored-hash", Role.ADMIN);
        when(users.findByUsername("frank")).thenReturn(Optional.of(user));
        when(hasher.matches(anyString(), eq("stored-hash"))).thenReturn(false);

        assertThatThrownBy(() -> handler.handle("frank", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownUserIsRejectedAfterStillCheckingAHash() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(hasher.matches(anyString(), eq("dummy-hash"))).thenReturn(false);

        assertThatThrownBy(() -> handler.handle("ghost", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(hasher).matches("whatever", "dummy-hash");
        org.mockito.Mockito.verifyNoInteractions(tokens);
    }

    @Test
    void neverIssuesTokenForUnknownUserEvenIfDummyHashMatches() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(hasher.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> handler.handle("ghost", "dummy"))
                .isInstanceOf(InvalidCredentialsException.class);
        org.mockito.Mockito.verify(tokens, org.mockito.Mockito.never()).issue(any());
    }
}
