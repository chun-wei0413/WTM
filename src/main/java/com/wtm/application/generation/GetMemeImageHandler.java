package com.wtm.application.generation;

import com.wtm.application.port.out.MemeRepository;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.domain.meme.Meme;
import com.wtm.domain.meme.MemeId;
import java.util.UUID;

/**
 * Returns the finished image of one of the caller's own memes, for downloading.
 */
public class GetMemeImageHandler {

    private final MemeRepository memes;
    private final ObjectStoragePort storage;

    public GetMemeImageHandler(MemeRepository memes, ObjectStoragePort storage) {
        this.memes = memes;
        this.storage = storage;
    }

    /** Someone else's meme, or one without an image, looks exactly like a missing one. */
    public MemeImage handle(UUID requesterId, UUID memeId) {
        Meme meme = memes.findById(new MemeId(memeId))
                .filter(m -> m.isOwnedBy(requesterId))
                .filter(m -> m.imageKey() != null)
                .orElseThrow(() -> new MemeNotFoundException(memeId));
        boolean jpeg = meme.imageKey().endsWith(".jpg") || meme.imageKey().endsWith(".jpeg");
        return new MemeImage(storage.get(meme.imageKey()),
                jpeg ? "image/jpeg" : "image/png",
                "meme-" + memeId + (jpeg ? ".jpg" : ".png"));
    }

    public record MemeImage(byte[] content, String contentType, String filename) {
    }
}
