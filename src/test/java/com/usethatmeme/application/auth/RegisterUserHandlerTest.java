package com.usethatmeme.application.auth;

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

import com.usethatmeme.application.TooManyRequestsException;
import com.usethatmeme.application.port.out.PasswordHasher;
import com.usethatmeme.application.port.out.RateLimiterPort;
import com.usethatmeme.application.port.out.UserRepository;
import com.usethatmeme.domain.user.Role;
import com.usethatmeme.domain.user.User;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegisterUserHandlerTest {

    private static final String CLIENT = "10.0.0.7";
    private static final RegistrationPolicy OPEN = new RegistrationPolicy(true, 5, Duration.ofHours(1));

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final RateLimiterPort limiter = mock(RateLimiterPort.class);
    private RegisterUserHandler handler;

    @BeforeEach
    void setUp() {
        when(limiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        when(hasher.hash(anyString())).thenReturn("hashed");
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        handler = new RegisterUserHandler(users, hasher, limiter, OPEN);
    }

    @Test
    void createsAnOrdinaryUserWithAHashedPassword() {
        User created = handler.handle("  frank_li  ", "correct horse", CLIENT);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(created);
        assertThat(created.username()).isEqualTo("frank_li");
        assertThat(created.role()).isEqualTo(Role.USER);
        assertThat(created.passwordHash()).isEqualTo("hashed");
        verify(hasher).hash("correct horse");
    }

    @Test
    void refusesWhenRegistrationIsClosed() {
        var closed = new RegisterUserHandler(users, hasher, limiter, new RegistrationPolicy(false, 5, Duration.ofHours(1)));

        assertThatThrownBy(() -> closed.handle("frank", "correct horse", CLIENT))
                .isInstanceOf(RegistrationClosedException.class);
        verify(users, never()).save(any());
    }

    @Test
    void refusesAnAddressThatRegistersTooOften() {
        when(limiter.tryAcquire("register:" + CLIENT, 5, OPEN.window())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle("frank", "correct horse", CLIENT))
                .isInstanceOf(TooManyRequestsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsUsernamesThatAreTooShortTooLongOrUseOddCharacters() {
        for (String bad : new String[] {"ab", "a".repeat(33), "has space", "名字很長", "semi;colon", "", null}) {
            assertThatThrownBy(() -> handler.handle(bad, "correct horse", CLIENT))
                    .as("username %s", bad).isInstanceOf(IllegalArgumentException.class);
        }
        verify(users, never()).save(any());
    }

    @Test
    void rejectsPasswordsThatAreTooShortTooLongOrEqualToTheUsername() {
        assertThatThrownBy(() -> handler.handle("frank", "short", CLIENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle("frank", "x".repeat(73), CLIENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle("frank_li", "FRANK_LI", CLIENT))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).save(any());
    }

    @Test
    void countsPasswordLengthInBytesBecauseBcryptCutsOffAt72() {
        // 25 Chinese characters are 75 bytes in UTF-8.
        assertThatThrownBy(() -> handler.handle("frank", "字".repeat(25), CLIENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(handler.handle("frank", "字".repeat(24), CLIENT).username()).isEqualTo("frank");
    }

    @Test
    void refusesANameThatIsAlreadyTaken() {
        when(users.findByUsername("frank")).thenReturn(Optional.of(User.register("Frank", "h", Role.USER)));

        assertThatThrownBy(() -> handler.handle("frank", "correct horse", CLIENT))
                .isInstanceOf(UsernameTakenException.class);
        verify(users, never()).save(any());
    }

    @Test
    void anotherRegistrationWinningTheRaceStillCountsAsTaken() {
        doThrow(new UsernameTakenException()).when(users).save(any());

        assertThatThrownBy(() -> handler.handle("frank", "correct horse", CLIENT))
                .isInstanceOf(UsernameTakenException.class);
    }
}
