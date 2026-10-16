package com.wtm.curation.usecase.port.in.command;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface TaggingQueueUseCase {

    List<UUID> claim(int limit);

    int recoverStale(Duration beingTaggedLongerThan, int maxAttempts);
}
