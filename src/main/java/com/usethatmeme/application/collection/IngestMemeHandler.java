package com.usethatmeme.application.collection;

import com.usethatmeme.application.UnsupportedImageException;
import com.usethatmeme.application.port.out.ImageFingerprintPort;
import com.usethatmeme.application.port.out.ImageFingerprintPort.Fingerprint;
import com.usethatmeme.application.port.out.ImageInspectorPort;
import com.usethatmeme.application.port.out.ImageInspectorPort.ImageInfo;
import com.usethatmeme.application.port.out.LibraryPort;
import com.usethatmeme.application.port.out.ObjectStoragePort;
import com.usethatmeme.domain.template.MemeTemplate;
import com.usethatmeme.domain.template.TemplateId;
import java.util.Optional;
import java.util.UUID;

/**
 * Adds one picture to the library, unless it is not a usable picture or the library already has it.
 * An added picture is a draft until the vision model has looked at it.
 */
public class IngestMemeHandler {

    /** Name given until the picture has been looked at and can be named after what it shows. */
    public static final String PLACEHOLDER_NAME = "未命名梗圖";

    /** Pictures smaller than this in either direction are icons, thumbnails or noise. */
    static final int MIN_SIDE = 100;

    /** Two perceptual hashes this many bits apart or fewer count as the same picture. */
    static final int MAX_HASH_DISTANCE = 4;

    private static final int MAX_NAME_LENGTH = 60;

    private final ImageInspectorPort inspector;
    private final ImageFingerprintPort fingerprints;
    private final LibraryPort library;
    private final ObjectStoragePort storage;

    public IngestMemeHandler(ImageInspectorPort inspector, ImageFingerprintPort fingerprints,
                             LibraryPort library, ObjectStoragePort storage) {
        this.inspector = inspector;
        this.fingerprints = fingerprints;
        this.library = library;
        this.storage = storage;
    }

    /**
     * @param suggestedName a title or file name for the picture, or null when there is none
     */
    public IngestOutcome handle(byte[] image, String suggestedName, Origin origin) {
        ImageInfo info;
        try {
            info = inspector.inspect(image);
        } catch (UnsupportedImageException e) {
            return IngestOutcome.rejected(e.getMessage());
        }
        if (info.width() < MIN_SIDE || info.height() < MIN_SIDE) {
            return IngestOutcome.rejected("The picture is too small (" + info.width() + "×" + info.height() + ")");
        }

        Fingerprint fingerprint = fingerprints.of(image);
        Optional<UUID> existing = library.findDuplicate(fingerprint.sha256(), fingerprint.perceptualHash(), MAX_HASH_DISTANCE);
        if (existing.isPresent()) {
            return IngestOutcome.duplicate(existing.get());
        }

        TemplateId id = TemplateId.newId();
        String key = "library/" + id.value() + "." + info.extension();
        MemeTemplate draft = MemeTemplate.draft(id, nameFor(suggestedName), key, info.width(), info.height());

        storage.put(key, image, info.contentType());
        try {
            library.add(draft, origin, fingerprint);
        } catch (DuplicateImageException e) {
            deleteQuietly(key, e);
            return IngestOutcome.duplicate(library.findDuplicate(fingerprint.sha256(), fingerprint.perceptualHash(), 0)
                    .orElse(null));
        } catch (RuntimeException e) {
            deleteQuietly(key, e);
            throw e;
        }
        return IngestOutcome.imported(id.value());
    }

    /** Turns a title or file name into a tidy display name. */
    static String nameFor(String suggested) {
        if (suggested == null) {
            return PLACEHOLDER_NAME;
        }
        String name = suggested.replaceAll("\\.[A-Za-z0-9]{2,5}$", "")   // file extension
                .replaceAll("[_\\-]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (name.isEmpty() || name.matches("[0-9a-fA-F \\-]{16,}")) {      // empty, or just a hash or number
            return PLACEHOLDER_NAME;
        }
        return name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                ? name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH))
                : name;
    }

    private void deleteQuietly(String key, RuntimeException cause) {
        try {
            storage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }
}
