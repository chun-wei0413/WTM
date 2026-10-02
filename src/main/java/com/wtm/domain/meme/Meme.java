package com.wtm.domain.meme;

import com.wtm.domain.DomainRuleViolation;
import com.wtm.domain.template.Slot;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A meme: a template plus the captions written for its slots.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Captions only go into slots the template had when the meme was composed.</li>
 *   <li>Every required slot has a caption, and no caption is longer than its slot allows.</li>
 *   <li>A template without slots yields a meme without captions (the image speaks for itself).</li>
 *   <li>The finished image is attached exactly once, and only a meme with an image can be kept.</li>
 * </ul>
 */
public class Meme {

    private final MemeId id;
    private final UUID ownerId;
    private final TemplateRef template;
    private final Map<Integer, String> captions;
    private MemeStatus status;
    private String imageKey;

    private Meme(MemeId id, UUID ownerId, TemplateRef template, Map<Integer, String> captions,
                 MemeStatus status, String imageKey) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.template = Objects.requireNonNull(template, "template");
        this.captions = new TreeMap<>(captions);
        this.status = status;
        this.imageKey = imageKey;
    }

    public static Meme compose(MemeId id, UUID ownerId, TemplateRef template, Map<Integer, String> proposed) {
        Map<Integer, String> accepted = new TreeMap<>();
        proposed.forEach((slotNo, text) -> {
            Slot slot = template.slot(slotNo).orElseThrow(
                    () -> new DomainRuleViolation("The template has no slot " + slotNo));
            String caption = text == null ? "" : text.strip();
            if (caption.isEmpty()) {
                return;
            }
            if (!slot.accepts(caption)) {
                throw new DomainRuleViolation(
                        "The caption for slot " + slotNo + " is longer than " + slot.maxChars() + " characters");
            }
            accepted.put(slotNo, caption);
        });
        for (Slot slot : template.slots()) {
            if (slot.required() && !accepted.containsKey(slot.slotNo())) {
                throw new DomainRuleViolation("Slot " + slot.slotNo() + " (" + slot.role() + ") needs a caption");
            }
        }
        return new Meme(id, ownerId, template, accepted, MemeStatus.COMPOSED, null);
    }

    /** Rebuilds a meme from storage. */
    public static Meme restore(MemeId id, UUID ownerId, TemplateRef template, Map<Integer, String> captions,
                               MemeStatus status, String imageKey) {
        return new Meme(id, ownerId, template, captions, status, imageKey);
    }

    public void attachImage(String key) {
        if (key == null || key.isBlank()) {
            throw new DomainRuleViolation("The image key must not be blank");
        }
        if (imageKey != null) {
            throw new DomainRuleViolation("This meme already has an image");
        }
        imageKey = key;
    }

    /** Marks the meme as one the owner wants to keep. Keeping twice changes nothing. */
    public void keep() {
        if (imageKey == null) {
            throw new DomainRuleViolation("A meme without an image cannot be kept");
        }
        status = MemeStatus.KEPT;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public MemeId id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public TemplateRef template() {
        return template;
    }

    public Map<Integer, String> captions() {
        return Collections.unmodifiableMap(captions);
    }

    public MemeStatus status() {
        return status;
    }

    public String imageKey() {
        return imageKey;
    }
}
