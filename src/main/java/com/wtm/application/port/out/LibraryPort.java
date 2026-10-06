package com.wtm.application.port.out;

import com.wtm.application.collection.DuplicateImageException;
import com.wtm.application.collection.Origin;
import com.wtm.application.collection.Reference;
import com.wtm.application.port.out.ImageFingerprintPort.Fingerprint;
import com.wtm.domain.template.MemeTemplate;
import java.util.Optional;
import java.util.UUID;

/**
 * Bookkeeping for the meme library: what is already in it, and adding something new.
 */
public interface LibraryPort {

    /**
     * Finds an entry that is the same picture: identical bytes, or a perceptual hash within
     * {@code maxDistance} differing bits.
     */
    Optional<UUID> findDuplicate(String sha256, long perceptualHash, int maxDistance);

    /**
     * Adds a new draft entry together with where it came from and its fingerprint, and puts it in
     * line to be looked at by the vision model. All of that happens together or not at all.
     *
     * @throws DuplicateImageException when identical bytes were added by someone else meanwhile
     */
    void add(MemeTemplate template, Origin origin, Fingerprint fingerprint);

    /** What the source said the meme is, when it said anything. */
    Optional<Reference> findReference(UUID templateId);

    /** How many entries there are, by what has happened to them. */
    LibraryStats stats();

    record LibraryStats(int total, int approved, int retired, int waitingForTags, int beingTagged,
                        int tagFailures) {
    }
}
