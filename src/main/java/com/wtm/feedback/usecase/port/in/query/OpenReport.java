package com.wtm.feedback.usecase.port.in.query;

import com.wtm.feedback.entity.ReportReason;
import java.time.Instant;
import java.util.UUID;

public record OpenReport(UUID id, UUID templateId, UUID userId, String username, ReportReason reason, String comment,
                  Instant createdAt) {
}
