package com.usethatmeme.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.usethatmeme.application.TooManyRequestsException;
import com.usethatmeme.application.port.out.GenerationJobStore;
import com.usethatmeme.application.port.out.GenerationJobStore.EnqueueResult;
import com.usethatmeme.application.port.out.GenerationJobStore.QuotaLimits;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmitGenerationHandlerTest {

    private static final QuotaLimits LIMITS = new QuotaLimits(2, 50);

    private final GenerationJobStore jobs = mock(GenerationJobStore.class);
    private final SubmitGenerationHandler handler = new SubmitGenerationHandler(jobs, LIMITS);
    private final UUID user = UUID.randomUUID();

    @Test
    void queuesTheTrimmedDescriptionWithTheConfiguredLimits() {
        when(jobs.enqueue(any(), any(), anyString(), any())).thenReturn(EnqueueResult.ACCEPTED);

        UUID jobId = handler.handle(user, "  週一又要上班  ");

        verify(jobs).enqueue(eq(jobId), eq(user), eq("週一又要上班"), eq(LIMITS));
    }

    @Test
    void rejectsEmptyAndTooLongDescriptionsWithoutQueueing() {
        assertThatThrownBy(() -> handler.handle(user, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(user, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(user, "字".repeat(301))).isInstanceOf(IllegalArgumentException.class);
        verify(jobs, never()).enqueue(any(), any(), anyString(), any());
    }

    @Test
    void tellsTheUserToWaitWhenTooManyRequestsAreStillInProgress() {
        when(jobs.enqueue(any(), any(), anyString(), any())).thenReturn(EnqueueResult.TOO_MANY_ACTIVE);

        assertThatThrownBy(() -> handler.handle(user, "hello"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfter())
                        .isEqualTo(Duration.ofSeconds(10)));
    }

    @Test
    void tellsTheUserWhenTodaysAllowanceIsUsedUp() {
        when(jobs.enqueue(any(), any(), anyString(), any())).thenReturn(EnqueueResult.DAILY_LIMIT_REACHED);

        assertThatThrownBy(() -> handler.handle(user, "hello"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("50")
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfter()).isNull());
    }
}
