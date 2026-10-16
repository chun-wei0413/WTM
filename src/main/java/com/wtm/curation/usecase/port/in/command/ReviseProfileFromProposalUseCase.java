package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.DomainRuleViolation;
import java.util.List;
import java.util.UUID;

/**
 * Writes the description the vision model proposed into a template, keeping its other names, and records
 * the change so that it can be taken back.
 */
public interface ReviseProfileFromProposalUseCase {

    /** @throws DomainRuleViolation when the proposal lacks a meaning or a usage example, or the template cannot change */
    void handle(Input input);

    /** @param automatic true when the rules decided, false when an administrator did */
    record Input(UUID templateId, String meaning, List<String> usageExamples, List<String> emotions,
                 String imageText, List<String> tags, boolean automatic) {
    }
}
