package com.memehub.domain.template;

import com.memehub.domain.DomainRuleViolation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A reusable meme image together with the knowledge of what it means and where
 * captions go. Operated by administrators only.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Every slot lies inside the image and slot numbers are unique.</li>
 *   <li>Only a complete profile (meaning and usage examples) can be approved,
 *       and an approved template keeps a complete profile.</li>
 *   <li>A template may have no slots at all (the image carries its own text).</li>
 *   <li>A retired template can never be changed again.</li>
 *   <li>The version increases whenever the slot layout of an approved template
 *       changes, so memes composed earlier can keep their own snapshot.</li>
 * </ul>
 */
public class MemeTemplate {

    private final TemplateId id;
    private final String name;
    private final String imageKey;
    private final int imageWidth;
    private final int imageHeight;
    private TemplateStatus status;
    private int version;
    private MemeProfile profile;
    private final Map<Integer, Slot> slots = new TreeMap<>();

    private MemeTemplate(TemplateId id, String name, String imageKey, int imageWidth, int imageHeight,
                         TemplateStatus status, int version, MemeProfile profile, List<Slot> slots) {
        if (name == null || name.isBlank()) {
            throw new DomainRuleViolation("Template name must not be blank");
        }
        if (imageKey == null || imageKey.isBlank()) {
            throw new DomainRuleViolation("Template image must be provided");
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new DomainRuleViolation("Image size must be positive");
        }
        this.id = id;
        this.name = name.strip();
        this.imageKey = imageKey;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.status = status;
        this.version = version;
        this.profile = profile;
        slots.forEach(slot -> this.slots.put(slot.slotNo(), slot));
    }

    public static MemeTemplate draft(TemplateId id, String name, String imageKey,
                                     int imageWidth, int imageHeight) {
        return new MemeTemplate(id, name, imageKey, imageWidth, imageHeight,
                TemplateStatus.DRAFT, 1, MemeProfile.empty(), List.of());
    }

    /** Rebuilds a template from storage. */
    public static MemeTemplate restore(TemplateId id, String name, String imageKey,
                                       int imageWidth, int imageHeight, TemplateStatus status,
                                       int version, MemeProfile profile, List<Slot> slots) {
        return new MemeTemplate(id, name, imageKey, imageWidth, imageHeight,
                status, version, profile, slots);
    }

    public void defineSlot(Slot slot) {
        requireNotRetired();
        requireInsideImage(slot);
        if (slots.containsKey(slot.slotNo())) {
            throw new DomainRuleViolation("Slot " + slot.slotNo() + " already exists");
        }
        slots.put(slot.slotNo(), slot);
        layoutChanged();
    }

    public void redefineSlot(Slot slot) {
        requireNotRetired();
        requireInsideImage(slot);
        if (!slots.containsKey(slot.slotNo())) {
            throw new DomainRuleViolation("Slot " + slot.slotNo() + " does not exist");
        }
        slots.put(slot.slotNo(), slot);
        layoutChanged();
    }

    public void removeSlot(int slotNo) {
        requireNotRetired();
        if (slots.remove(slotNo) == null) {
            throw new DomainRuleViolation("Slot " + slotNo + " does not exist");
        }
        layoutChanged();
    }

    public void reviseProfile(MemeProfile newProfile) {
        requireNotRetired();
        if (status == TemplateStatus.APPROVED && !newProfile.isComplete()) {
            throw new DomainRuleViolation("An approved template must keep a complete profile");
        }
        this.profile = newProfile;
    }

    public void approve() {
        if (status != TemplateStatus.DRAFT) {
            throw new DomainRuleViolation("Only a draft template can be approved, but it is " + status);
        }
        if (!profile.isComplete()) {
            throw new DomainRuleViolation("Meaning and usage examples are required before approval");
        }
        status = TemplateStatus.APPROVED;
    }

    public void retire() {
        requireNotRetired();
        status = TemplateStatus.RETIRED;
    }

    public boolean isUsable() {
        return status == TemplateStatus.APPROVED;
    }

    public TemplateId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String imageKey() {
        return imageKey;
    }

    public int imageWidth() {
        return imageWidth;
    }

    public int imageHeight() {
        return imageHeight;
    }

    public TemplateStatus status() {
        return status;
    }

    public int version() {
        return version;
    }

    public MemeProfile profile() {
        return profile;
    }

    public List<Slot> slots() {
        return Collections.unmodifiableList(new ArrayList<>(slots.values()));
    }

    private void layoutChanged() {
        if (status == TemplateStatus.APPROVED) {
            version++;
        }
    }

    private void requireInsideImage(Slot slot) {
        if (!slot.fitsWithin(imageWidth, imageHeight)) {
            throw new DomainRuleViolation("Slot " + slot.slotNo() + " lies outside the image");
        }
    }

    private void requireNotRetired() {
        if (status == TemplateStatus.RETIRED) {
            throw new DomainRuleViolation("A retired template can no longer be changed");
        }
    }
}
