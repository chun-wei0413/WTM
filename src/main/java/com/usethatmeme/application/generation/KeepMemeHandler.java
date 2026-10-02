package com.usethatmeme.application.generation;

import com.usethatmeme.application.port.out.MemeRepository;
import com.usethatmeme.domain.meme.Meme;
import com.usethatmeme.domain.meme.MemeId;
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
