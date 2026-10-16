package com.wtm.feedback.usecase.port.in.command;

import java.util.UUID;

public interface ResolveReportsUseCase {

    /**
     * Writes the proposed description into the meme (keeping its other names) and closes its reports.
     *
     * @throws DomainRuleViolation when there is no finished proposal, or it is not a usable description
     */
    void apply(UUID templateId);

    /** The same, decided by the rules because enough trusted people agreed with the model. */
    void applyAutomatically(UUID templateId);

    /** Closes the reports without changing the meme. */
    void dismiss(UUID templateId);

    /** The same, decided by the rules because the model found nothing wrong and few people insisted. */
    void dismissAutomatically(UUID templateId, String note);

    /** Asks the vision model to look at the meme again, whatever the daily budget says. */
    void reanalyze(UUID templateId);

    /**
     * Takes back what the rules did to a meme: puts the earlier description back, or opens the reports they
     * closed again (and has the model look once more).
     *
     * @throws DomainRuleViolation when there is nothing to take back, or the description was changed since
     */
    void undoAutomatic(UUID templateId);
}
