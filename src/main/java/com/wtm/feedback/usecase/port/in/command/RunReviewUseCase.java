package com.wtm.feedback.usecase.port.in.command;

import java.util.UUID;

public interface RunReviewUseCase {

    void handle(UUID templateId);
}
