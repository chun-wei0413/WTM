package com.wtm.feedback.usecase.port.in.command;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface ReviewQueueUseCase {

    List<UUID> claim(int limit);

    int recoverStale(Duration runningLongerThan, int maxAttempts);
}
