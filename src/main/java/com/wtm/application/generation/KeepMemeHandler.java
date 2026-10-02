package com.wtm.application.generation;

import com.wtm.application.port.out.MemeRepository;
import com.wtm.domain.meme.Meme;
import com.wtm.domain.meme.MemeId;
import java.util.UUID;

public class KeepMemeHandler {

    private final MemeRepository memes;

    public KeepMemeHandler(MemeRepository memes) {
        this.memes = memes;
    }

    /** Someone else's meme looks exactly like a missing one. */
    public void handle(UUID requesterId, UUID memeId) {
        Meme meme = memes.findById(new MemeId(memeId))
                .filter(m -> m.isOwnedBy(requesterId))
                .orElseThrow(() -> new MemeNotFoundException(memeId));
        meme.keep();
        memes.save(meme);
    }
}
