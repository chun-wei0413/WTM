package com.wtm.application.port.out;

import com.wtm.domain.template.MemeProfile;
import java.util.Optional;
import java.util.UUID;

/**
 * What a meme's description was before reports changed it, so a change can be taken back.
 */
public interface ProfileHistoryPort {

    void record(UUID templateId, MemeProfile before, MemeProfile after, Source source);

    /** The most recent change the rules made to the meme that has not been taken back yet. */
    Optional<Change> latestAutomaticChange(UUID templateId);

    void markUndone(UUID changeId);

    enum Source {
        ADMIN,
        AUTO
    }

    record Change(UUID id, MemeProfile before, MemeProfile after) {
    }
}
