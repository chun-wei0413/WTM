package com.wtm.curation.entity;

import com.wtm.shared.DomainRuleViolation;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.Slot;
import com.wtm.shared.TemplateId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

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
 *   <li>A description replaced by a proposal is remembered, and the latest change the rules made can be taken
 *       back, but only while nobody has changed the description since.</li>
 *   <li>The version increases whenever the slot layout of an approved template
 *       changes, so memes composed earlier can keep their own snapshot.</li>
 * </ul>
 */
public class MemeTemplate {

    private final TemplateId id;
    private String name;
    private final String imageKey;
    private final int imageWidth;
    private final int imageHeight;
    private TemplateStatus status;
    private int version;
    private MemeProfile profile;
    private final Map<Integer, Slot> slots = new TreeMap<>();
    private final List<ProfileChange> profileChanges = new ArrayList<>();

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

    /** Rebuilds a template from storage, with the changes of its description that have not been taken back. */
    public static MemeTemplate restore(TemplateId id, String name, String imageKey,
                                       int imageWidth, int imageHeight, TemplateStatus status,
                                       int version, MemeProfile profile, List<Slot> slots,
                                       List<ProfileChange> profileChanges) {
        MemeTemplate template = restore(id, name, imageKey, imageWidth, imageHeight, status, version, profile, slots);
        template.profileChanges.addAll(profileChanges);
        return template;
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

    /** Gives the template a better name, for example once the picture has been looked at. */
    public void rename(String newName) {
        requireNotRetired();
        if (newName == null || newName.isBlank()) {
            throw new DomainRuleViolation("Template name must not be blank");
        }
        this.name = newName.strip();
    }

    public void reviseProfile(MemeProfile newProfile) {
        requireNotRetired();
        if (status == TemplateStatus.APPROVED && !newProfile.isComplete()) {
            throw new DomainRuleViolation("An approved template must keep a complete profile");
        }
        this.profile = newProfile;
    }

    /**
     * Replaces the description with a proposal and remembers what it was, so that the change can be taken back.
     *
     * @param source who decided: the rules, or an administrator
     */
    public void adoptProfile(MemeProfile proposal, ProfileChange.Source source, Instant now) {
        MemeProfile before = profile;
        reviseProfile(proposal);
        profileChanges.add(new ProfileChange(UUID.randomUUID(), before, proposal, source, now, null));
    }

    /** Whether the rules changed the description and nothing has taken that back yet. */
    public boolean canRestoreProfile() {
        return latestAutomaticChange() >= 0;
    }

    /**
     * Puts back the description from before the latest change the rules made.
     *
     * @return false when there is no such change to take back
     * @throws DomainRuleViolation when the description was changed again since, so it is not put back
     */
    public boolean restoreProfile(Instant now) {
        int index = latestAutomaticChange();
        if (index < 0) {
            return false;
        }
        ProfileChange change = profileChanges.get(index);
        if (!profile.equals(change.after())) {
            throw new DomainRuleViolation("The description was changed again since, so it is not put back");
        }
        reviseProfile(change.before());
        profileChanges.set(index, change.undone(now));
        return true;
    }

    /** The changes to the description that are remembered, oldest first. */
    public List<ProfileChange> profileChanges() {
        return Collections.unmodifiableList(profileChanges);
    }

    private int latestAutomaticChange() {
        int latest = -1;
        for (int i = 0; i < profileChanges.size(); i++) {
            ProfileChange change = profileChanges.get(i);
            if (change.source() == ProfileChange.Source.AUTO && !change.isUndone()
                    && (latest < 0 || !change.createdAt().isBefore(profileChanges.get(latest).createdAt()))) {
                latest = i;
            }
        }
        return latest;
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
