package com.wtm.account.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.account.entity.RegistrationPolicy;
import com.wtm.account.entity.Role;
import com.wtm.account.entity.User;
import com.wtm.account.usecase.port.in.RegistrationClosedException;
import com.wtm.account.usecase.port.in.UsernameTakenException;
import com.wtm.account.usecase.port.in.command.RegisterUserUseCase;
import com.wtm.account.usecase.port.out.PasswordHasher;
import com.wtm.account.usecase.port.out.UserRepository;
import com.wtm.shared.RateLimiterPort;
import com.wtm.shared.TooManyRequestsException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegisterUserServiceTest {

    private static final String CLIENT = "10.0.0.7";
    private static final RegistrationPolicy OPEN = new RegistrationPolicy(true, 5, Duration.ofHours(1));

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final RateLimiterPort limiter = mock(RateLimiterPort.class);
    private RegisterUserService handler;

    @BeforeEach
    void setUp() {
        when(limiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        when(hasher.hash(anyString())).thenReturn("hashed");
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        handler = new RegisterUserService(users, hasher, limiter, OPEN);
    }

    @Test
    void createsAnOrdinaryUserWithAHashedPassword() {
        var created = handler.handle(new RegisterUserUseCase.Input("  frank_li  ", "correct horse", CLIENT));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(created).isEqualTo(new RegisterUserUseCase.Output(saved.getValue().id(), "frank_li"));
        assertThat(saved.getValue().username()).isEqualTo("frank_li");
        assertThat(saved.getValue().role()).isEqualTo(Role.USER);
        assertThat(saved.getValue().passwordHash()).isEqualTo("hashed");
        verify(hasher).hash("correct horse");
    }

    @Test
    void refusesWhenRegistrationIsClosed() {
        var closed = new RegisterUserService(users, hasher, limiter, new RegistrationPolicy(false, 5, Duration.ofHours(1)));

        assertThatThrownBy(() -> closed.handle(new RegisterUserUseCase.Input("frank", "correct horse", CLIENT)))
                .isInstanceOf(RegistrationClosedException.class);
        verify(users, never()).save(any());
    }

    @Test
    void refusesAnAddressThatRegistersTooOften() {
        when(limiter.tryAcquire("register:" + CLIENT, 5, OPEN.window())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new RegisterUserUseCase.Input("frank", "correct horse", CLIENT)))
                .isInstanceOf(TooManyRequestsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsUsernamesThatAreTooShortTooLongOrUseOddCharacters() {
        for (String bad : new String[] {"ab", "a".repeat(33), "has space", "名字很長", "semi;colon", "", null}) {
            assertThatThrownBy(() -> handler.handle(new RegisterUserUseCase.Input(bad, "correct horse", CLIENT)))
                    .as("username %s", bad).isInstanceOf(IllegalArgumentException.class);
        }
        verify(users, never()).save(any());
    }

    @Test
    void rejectsPasswordsThatAreTooShortTooLongOrEqualToTheUsername() {
        assertThatThrownBy(() -> handler.handle(new RegisterUserUseCase.Input("frank", "short", CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(new RegisterUserUseCase.Input("frank", "x".repeat(73), CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(new RegisterUserUseCase.Input("frank_li", "FRANK_LI", CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
    }

    @Test
    void countsPasswordLengthInBytesBecauseBcryptCutsOffAt72() {
        // 25 Chinese characters are 75 bytes in UTF-8.
        assertThatThrownBy(() -> handler.handle(new RegisterUserUseCase.Input("frank", "字".repeat(25), CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(handler.handle(new RegisterUserUseCase.Input("frank", "字".repeat(24), CLIENT)).username()).isEqualTo("frank");
    }

    @Test
    void refusesANameThatIsAlreadyTaken() {
        when(users.findByUsername("frank")).thenReturn(Optional.of(User.register("Frank", "h", Role.USER)));

        assertThatThrownBy(() -> handler.handle(new RegisterUserUseCase.Input("frank", "correct horse", CLIENT)))
                .isInstanceOf(UsernameTakenException.class);
        verify(users, never()).save(any());
    }

    @Test
    void anotherRegistrationWinningTheRaceStillCountsAsTaken() {
        doThrow(new UsernameTakenException()).when(users).save(any());

        assertThatThrownBy(() -> handler.handle(new RegisterUserUseCase.Input("frank", "correct horse", CLIENT)))
                .isInstanceOf(UsernameTakenException.class);
    }
}
