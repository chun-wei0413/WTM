package com.wtm.feedback.usecase.port.in.command;

import com.wtm.feedback.entity.ReportReason;
import com.wtm.shared.UserId;
import java.util.UUID;

public interface SubmitReportUseCase {

    /**
     * @throws IllegalArgumentException for a missing reason or a comment that is too long
     * @throws TemplateNotFoundException when there is no published meme with that id
     * @throws TooManyRequestsException when the person has reported too much today or has too many reports waiting
     */
    void handle(Input input);

    record Input(UserId userId, UUID templateId, ReportReason reason, String comment) {
    }
}
