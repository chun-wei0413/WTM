package com.wtm.account.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wtm.account.entity.LoginPolicy;
import com.wtm.account.entity.Role;
import com.wtm.account.entity.User;
import com.wtm.account.usecase.port.in.InvalidCredentialsException;
import com.wtm.account.usecase.port.in.command.LoginUseCase;
import com.wtm.account.usecase.port.out.PasswordHasher;
import com.wtm.account.usecase.port.out.TokenIssuer;
import com.wtm.account.usecase.port.out.TokenIssuer.IssuedToken;
import com.wtm.account.usecase.port.out.UserRepository;
import com.wtm.shared.RateLimiterPort;
import com.wtm.shared.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginServiceTest {

    private static final String CLIENT = "10.0.0.7";
    private static final LoginPolicy POLICY = new LoginPolicy(5, 30, Duration.ofMinutes(15));

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final TokenIssuer tokens = mock(TokenIssuer.class);
    private final RateLimiterPort limiter = mock(RateLimiterPort.class);
    private LoginService handler;

    @BeforeEach
    void setUp() {
        when(hasher.hash(anyString())).thenReturn("dummy-hash");
        handler = new LoginService(users, hasher, tokens, limiter, POLICY);
    }

    @Test
    void issuesTokenWhenCredentialsMatch() {
        User user = User.register("frank", "stored-hash", Role.ADMIN);
        IssuedToken token = new IssuedToken("jwt", Instant.now());
        when(users.findByUsername("frank")).thenReturn(Optional.of(user));
        when(hasher.matches("secret", "stored-hash")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(token);

        assertThat(handler.handle(new LoginUseCase.Input("frank", "secret", CLIENT))).isSameAs(token);
    }

    @Test
    void rejectsWrongPassword() {
        User user = User.register("frank", "stored-hash", Role.ADMIN);
        when(users.findByUsername("frank")).thenReturn(Optional.of(user));
        when(hasher.matches(anyString(), eq("stored-hash"))).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginUseCase.Input("frank", "wrong", CLIENT)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownUserIsRejectedAfterStillCheckingAHash() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(hasher.matches(anyString(), eq("dummy-hash"))).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginUseCase.Input("ghost", "whatever", CLIENT)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(hasher).matches("whatever", "dummy-hash");
        verifyNoInteractions(tokens);
    }

    @Test
    void neverIssuesTokenForUnknownUserEvenIfDummyHashMatches() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(hasher.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new LoginUseCase.Input("ghost", "dummy", CLIENT)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokens, never()).issue(any());
    }

    @Test
    void countsFailuresForTheAccountAndForTheAddress() {
        when(users.findByUsername("Frank")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new LoginUseCase.Input("Frank", "wrong", CLIENT)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(limiter).tryAcquire("login:" + CLIENT + "|frank", 5, POLICY.window());
        verify(limiter).tryAcquire("login:" + CLIENT, 30, POLICY.window());
    }

    @Test
    void refusesWithoutCheckingThePasswordOnceTheAccountIsLocked() {
        when(limiter.isLimited(eq("login:" + CLIENT + "|frank"), anyInt(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new LoginUseCase.Input("frank", "right-password", CLIENT)))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfter()).isEqualTo(POLICY.window()));
        verify(users, never()).findByUsername(anyString());
        verify(hasher, never()).matches(anyString(), anyString());
        verifyNoInteractions(tokens);
    }

    @Test
    void refusesEverythingFromAnAddressThatFailedTooOften() {
        when(limiter.isLimited(eq("login:" + CLIENT), anyInt(), any())).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new LoginUseCase.Input("anyone", "anything", CLIENT)))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aSuccessfulLoginClearsTheFailuresOfThatAccount() {
        User user = User.register("frank", "stored-hash", Role.USER);
        when(users.findByUsername("frank")).thenReturn(Optional.of(user));
        when(hasher.matches("secret", "stored-hash")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(new IssuedToken("jwt", Instant.now()));

        handler.handle(new LoginUseCase.Input("frank", "secret", CLIENT));

        verify(limiter).reset("login:" + CLIENT + "|frank");
        verify(limiter, never()).tryAcquire(anyString(), anyInt(), any());
    }
}
