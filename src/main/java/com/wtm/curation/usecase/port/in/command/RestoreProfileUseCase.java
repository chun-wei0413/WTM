package com.wtm.curation.usecase.port.in.command;

import com.wtm.shared.DomainRuleViolation;
import java.util.UUID;

/**
 * Takes back what the rules did to a template's description.
 */
public interface RestoreProfileUseCase {

    /**
     * Puts the earlier description back.
     *
     * @return false when the rules have changed nothing that is still to be taken back
     * @throws DomainRuleViolation when the description was changed again since, so it is not put back
     */
    boolean handle(UUID templateId);

    /** Whether there is a change by the rules that could be taken back. */
    boolean canRestore(UUID templateId);
}
